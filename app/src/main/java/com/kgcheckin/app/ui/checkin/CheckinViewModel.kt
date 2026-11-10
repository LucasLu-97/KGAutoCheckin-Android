package com.kgcheckin.app.ui.checkin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kgcheckin.app.KgApplication
import com.kgcheckin.app.core.CheckinLogBus
import com.kgcheckin.app.core.LogEvent
import com.kgcheckin.app.data.Account
import com.kgcheckin.app.data.CheckinCoordinator
import com.kgcheckin.app.data.CheckinOutcome
import com.kgcheckin.app.data.CheckinSettings
import com.kgcheckin.app.schedule.CheckinScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 签到领取页 ViewModel：
 * - 多账号选择（默认全部），手动触发，逐账号串行执行（防风控）
 * - 实时日志流入 UI；完成后系统通知
 * - 定时自动化设置（方案A）与打开即签（方案B）开关
 * - 与定时服务共用进程级执行锁，绝不并发重复执行
 */
class CheckinViewModel(app: Application) : AndroidViewModel(app) {

    private val container = getApplication<KgApplication>().container
    private val repo = container.repository
    private val notifier = container.notifier

    val accounts: StateFlow<List<Account>> = repo.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<CheckinSettings> = container.settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CheckinSettings())

    /** 选中的待签到账号 userid 集合；为空表示跟随全部 */
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEvent>>(emptyList())
    val logs: StateFlow<List<LogEvent>> = _logs.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** 最近一次完成的汇总（用于页面顶部结果条） */
    private val _lastSummary = MutableStateFlow<String?>(null)
    val lastSummary: StateFlow<String?> = _lastSummary.asStateFlow()

    private var checkinJob: Job? = null

    companion object {
        /** 打开即签：每进程最多自动触发一次 */
        private var autoTried = false
    }

    init {
        // 恢复今天历次执行（手动/定时/打开即签）的日志，重启 APP 也不丢
        _logs.value = container.logArchive.loadDay(LocalDate.now())
        // 订阅日志总线：定时服务在后台执行时，打开 APP 即可看到实时过程
        viewModelScope.launch {
            CheckinLogBus.events.collect { event ->
                _logs.value = _logs.value + event
            }
        }
        // 重置应用数据后清空内存中的日志视图（日志文件已由设置页删除）
        viewModelScope.launch {
            CheckinLogBus.clearSignals.collect {
                _logs.value = emptyList()
                _lastSummary.value = null
            }
        }
        // 清理过期日志文件（保留30天）
        viewModelScope.launch { container.logArchive.cleanup() }
    }

    private fun emit(event: LogEvent) {
        _logs.value = _logs.value + event
        container.logArchive.append(event)
    }

    fun toggleSelect(userid: String) {
        _selected.value = _selected.value.toMutableSet().apply {
            if (contains(userid)) remove(userid) else add(userid)
        }
    }

    // ══════════════════════ 手动签到 ══════════════════════

    fun start() {
        if (_running.value) return
        val all = accounts.value
        if (all.isEmpty()) {
            _lastSummary.value = "还没有登录账号，请先到「账户登录」页添加账号"
            return
        }
        val targets = if (_selected.value.isEmpty()) all else all.filter { _selected.value.contains(it.userid) }
        if (targets.isEmpty()) {
            _lastSummary.value = "请至少选择一个账号"
            return
        }
        if (!CheckinCoordinator.tryAcquire()) {
            _lastSummary.value = "已有签到执行正在进行（可能是定时服务），请稍候"
            return
        }

        _running.value = true
        _lastSummary.value = null
        checkinJob = viewModelScope.launch {
            try {
                emit(LogEvent.info("==== 开始签到（共 ${targets.size} 个账号）===="))
                val results = mutableListOf<CheckinOutcome>()
                for (account in targets) {
                    try {
                        results.add(repo.runCheckin(account) { emit(it) })
                    } catch (e: CancellationException) {
                        emit(LogEvent.warn("签到已取消"))
                        throw e
                    } catch (e: Exception) {
                        emit(LogEvent.error("账号异常终止：${e.message}"))
                    }
                }
                emit(LogEvent.info("==== 签到结束 ===="))
                val success = results.count { it.success }
                _lastSummary.value = when {
                    results.isEmpty() -> "签到未完成"
                    success == results.size -> "全部成功：$success/${results.size} 个账号"
                    success == 0 -> "全部失败：0/${results.size} 个账号"
                    else -> "部分成功：$success/${results.size} 个账号"
                }
                notifier.notifyCheckinResult(results)
            } finally {
                CheckinCoordinator.release()
                _running.value = false
            }
        }
    }

    fun cancel() {
        checkinJob?.cancel()
        emit(LogEvent.warn("已手动取消本次签到"))
    }

    /** 清除日志：清空页面日志、删除当天存档并重置耗时展示 */
    fun clearLogs() {
        _logs.value = emptyList()
        container.logArchive.clearDay(LocalDate.now())
        CheckinCoordinator.resetLastDuration()
        _lastSummary.value = null
    }

    // ══════════════════════ 自动化设置（方案A）══════════════════════

    /** 开关每日定时；开启即在窗口内抽定下一个随机时刻并排布闹钟 */
    fun setAutoEnabled(enabled: Boolean) {
        val app = getApplication<KgApplication>()
        viewModelScope.launch {
            container.settingsStore.setAutoEnabled(enabled)
            if (enabled) {
                CheckinScheduler.ensureArmed(app, allowToday = true)
            } else {
                CheckinScheduler.cancel(app)
            }
        }
    }

    /** 修改签到时间窗口；已开启则作废旧抽定时刻、按新窗口重抽 */
    fun setAutoWindow(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        val app = getApplication<KgApplication>()
        viewModelScope.launch {
            container.settingsStore.setWindow(startHour, startMinute, endHour, endMinute)
            // 无论开关状态都作废旧抽定时刻，避免旧窗口的残余时刻被误用
            container.settingsStore.setNextPick(0L)
            val s = container.settingsStore.settings.first()
            if (s.autoEnabled) {
                CheckinScheduler.ensureArmed(app, allowToday = true)
            }
        }
    }

    /** 开关打开即签（方案B） */
    fun setOpenToCheckin(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsStore.setOpenToCheckin(enabled)
        }
    }

    // ══════════════════════ 打开即签（方案B）══════════════════════

    /**
     * APP 进入前台时检查：开启“打开即签”且存在今日未签账号 → 自动开始。
     * 幂等：脚本对“今日已领”本来就会正确处理，不会重复领取。
     */
    fun maybeAutoStartOnOpen() {
        if (autoTried) return
        autoTried = true
        viewModelScope.launch {
            // 进程冷启动（如系统后台拉活/自启动）时账号可能尚未从磁盘异步加载完成，
            // 先幂等地等待加载，避免“打开即签”误判为无账号而静默跳过
            container.accountStore.loadIfNeeded()
            val s = container.settingsStore.settings.first()
            if (!s.openToCheckin) return@launch
            val accounts = repo.accounts.first()
            if (accounts.isEmpty()) return@launch
            if (CheckinCoordinator.running.value || _running.value) return@launch

            val today = LocalDate.now().toEpochDay()
            val anyPending = accounts.any { acc ->
                runCatching { container.recordDao.findDay(acc.userid, today) }.getOrNull() == null
            }
            if (anyPending) {
                emit(LogEvent.info("「打开即签」已开启，自动开始签到..."))
                start()
            }
        }
    }
}
