package com.kgcheckin.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kgcheckin.app.MainActivity
import com.kgcheckin.app.R
import com.kgcheckin.app.appContainer
import com.kgcheckin.app.core.LogEvent
import com.kgcheckin.app.core.LogLevel
import com.kgcheckin.app.data.CheckinCoordinator
import com.kgcheckin.app.data.CheckinOutcome
import com.kgcheckin.app.schedule.CheckinScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 每日定时签到前台服务（一体化架构）：
 * 由精确闹钟触发，直接调用 KgRepository 后台静默执行全部账号。
 */
class CheckinService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var acquired = false
    private lateinit var nm: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "签到进度", NotificationManager.IMPORTANCE_LOW).apply {
                description = "定时签到执行过程中的实时进度"
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                if (job != null) {
                    job?.cancel() // job 的 finally 中完成释放锁/续排/收尾
                } else {
                    scheduleNext()
                    finishRun()
                }
            }
            else -> {
                startAsForeground()
                when {
                    job?.isActive == true -> Unit
                    !CheckinCoordinator.tryAcquire() -> {
                        updateProgress("检测到APP内正在签到，定时服务退出")
                        appContainer.notifier.notifySkipped("APP 内正在手动签到，本次定时触发自动避让")
                        scheduleNext()
                        finishRun()
                    }
                    else -> {
                        acquired = true
                        job = scope.launch {
                            holdWakeLock()
                            try {
                                runAllAccounts()
                            } catch (e: CancellationException) {
                                updateProgress("签到已取消")
                            } finally {
                                if (acquired) {
                                    CheckinCoordinator.release()
                                    acquired = false
                                }
                                releaseWakeLock()
                                scheduleNext()
                                finishRun()
                            }
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        job?.cancel()
        stopSelf()
    }

    override fun onDestroy() {
        job?.cancel()
        if (acquired) {
            CheckinCoordinator.release()
            acquired = false
        }
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    /** 执行全部账号；过程日志三路分发（存档 + 总线 + 通知） */
    private suspend fun runAllAccounts() {
        val container = appContainer
        // 关键修复：定时闹钟常在 APP 被杀后冷启动进程。AppContainer 启动时的
        // 异步账号加载与本服务的执行存在竞态——accounts.first() 会立刻取到
        // StateFlow 的初始空列表，导致“暂无已登录账号，跳过本次执行”。
        // 这里先幂等地等待加密账号仓库加载完成（loadIfNeeded 内部双检锁，
        // 进程已加载时立即返回，前台定期触发场景零额外开销）。
        container.accountStore.loadIfNeeded()
        val accounts = container.accountStore.accounts.first()
        if (accounts.isEmpty()) {
            // 与其余日志一致走 postLog 三路分发（存档 + 总线 + 通知栏节流更新），
            // 正在看通知栏的用户也能直接看到本次跳过的原因
            postLog(LogEvent.warn("暂无已登录账号，跳过本次执行"))
            // 跳过路径瞬时结束，前台服务通知存活不足1秒不可见；补静音留痕通知
            container.notifier.notifySkipped("暂无已登录账号，本次定时触发已跳过")
            return
        }

        // 幂等保护：全部账号当日均已成功签到则直接跳过。
        // Doze/部分机型存在同一闹钟被延迟后多次投递的情况——已全部签到时重复
        // 执行只会刷出多余日志；未全部签到时重复触发仍会正常补签（接口侧幂等）。
        val today = LocalDate.now().toEpochDay()
        val allDoneToday = accounts.all { acc ->
            runCatching { container.recordDao.findDay(acc.userid, today) }.getOrNull()?.success == true
        }
        if (allDoneToday) {
            postLog(LogEvent.info("今日各账号均已签到成功，跳过本次定时执行"))
            container.notifier.notifySkipped("今日已全部签到成功，本次定时触发自动跳过")
            return
        }

        // 网络就绪等待：闹钟常在息屏/刚解锁时触发，Wi-Fi 可能尚未重连完成，
        // 立即执行会导致请求全部失败；等待最多60秒再开始。
        if (!awaitNetwork()) {
            postLog(LogEvent.warn("等待网络超时（${NETWORK_WAIT_SECONDS}秒），仍尝试继续执行"))
        }

        postLog(LogEvent.info("==== 定时签到开始（共 ${accounts.size} 个账号）===="))
        updateProgress("开始定时签到（共 ${accounts.size} 个账号）")
        val results = mutableListOf<CheckinOutcome>()
        accounts.forEachIndexed { idx, account ->
            updateProgress("签到中：${account.displayNickname()}（${idx + 1}/${accounts.size}）")
            try {
                results.add(container.repository.runCheckin(account) { event -> postLog(event) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                postLog(LogEvent.error("账号 ${account.displayNickname()} 执行异常：${e.message}"))
            }
        }
        postLog(LogEvent.info("==== 定时签到结束 ===="))
        container.notifier.notifyCheckinResult(results)
    }

    /** 等待网络就绪（VALIDATED），2秒轮询直到超时；等待期间可被取消 */
    private suspend fun awaitNetwork(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return true
        val deadline = System.currentTimeMillis() + NETWORK_WAIT_SECONDS * 1000L
        while (System.currentTimeMillis() < deadline) {
            if (isNetworkValidated(cm)) return true
            updateProgress("等待网络连接...")
            delay(2000)
        }
        return isNetworkValidated(cm)
    }

    /**
     * 网络可用性判断。任何异常（权限缺失、系统服务异常等）都视为“可用”，
     * 绝不因网络状态查询本身阻断签到或导致崩溃。
     */
    private fun isNetworkValidated(cm: ConnectivityManager): Boolean = runCatching {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(true)

    /** 日志三路分发：通知栏（节流） + 持久化存档 + APP内实时总线 */
    private fun postLog(event: LogEvent) {
        appContainer.logArchive.append(event)
        com.kgcheckin.app.core.CheckinLogBus.post(event)
        onLog(event)
    }

    @Volatile private var lastNotifyAt = 0L

    private fun onLog(event: LogEvent) {
        val now = System.currentTimeMillis()
        val important = event.level == LogLevel.ACCOUNT ||
            event.level == LogLevel.SUCCESS ||
            event.level == LogLevel.ERROR
        if (important || now - lastNotifyAt >= 1500) {
            lastNotifyAt = now
            updateProgress(event.message)
        }
    }

    private fun updateProgress(text: String) {
        nm.notify(NOTIFY_ID, buildNotification(text))
    }

    private fun startAsForeground() {
        val notification = buildNotification("准备签到...")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFY_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFY_ID, notification)
        }
    }

    /**
     * 续排明天的闹钟：跑在进程级 applicationScope（不随 Service 销毁而取消），
     * NonCancellable 保证续排必然完成。
     * 修复：原先在 stopSelf 后用 Service 自身 scope 续排，onDestroy 的 scope.cancel()
     * 与续排协程竞态，可能导致闹钟丢失、次日定时不触发。
     */
    private fun scheduleNext() {
        appContainer.applicationScope.launch {
            withContext(NonCancellable) {
                runCatching {
                    val settings = appContainer.settingsStore.settings.first()
                    if (settings.autoEnabled) {
                        CheckinScheduler.schedule(this@CheckinService, settings.hour, settings.minute)
                    }
                }
            }
        }
    }

    /** 收尾：撤销前台 + 停止服务（闹钟续排由 scheduleNext 独立完成） */
    private fun finishRun() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(text: String): Notification {
        // launcher intent：APP 在后台时仅带回前台，不销毁重建 Activity
        val openApp = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, CheckinService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_checkin)
            .setContentTitle("定时签到进行中")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", stop)
            .build()
    }

    private fun holdWakeLock() {
        runCatching {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kgcheckin:checkin").apply {
                    setReferenceCounted(false)
                    acquire(WAKELOCK_TIMEOUT_MS)
                }
        }.onFailure { wakeLock = null }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    companion object {
        const val ACTION_STOP = "com.kgcheckin.app.action.STOP_CHECKIN"
        private const val CHANNEL_PROGRESS = "checkin_progress"
        private const val NOTIFY_ID = 1002
        private const val NETWORK_WAIT_SECONDS = 60
        private const val WAKELOCK_TIMEOUT_MS = 15 * 60 * 1000L

        fun intent(context: Context): Intent = Intent(context, CheckinService::class.java)

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, intent(context))
        }
    }
}