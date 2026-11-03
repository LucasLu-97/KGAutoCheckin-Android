package com.kgcheckin.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kgcheckin.app.MainActivity
import com.kgcheckin.app.R
import com.kgcheckin.app.data.CheckinOutcome

/** 签到结果系统通知（横幅提醒） */
class Notifier(private val context: Context) {

    private val channelId = "checkin_result"
    private val infoChannelId = "checkin_info"

    init {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                "签到结果通知",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "签到领取完成后的结果提醒（成功或失败）"
                enableLights(true)
            },
        )
        // 定时触发但未实际执行时的静音提醒通道：不响铃不振动，仅在通知栏留痕，
        // 让“被划掉后台”场景下的触发可见可查
        manager.createNotificationChannel(
            NotificationChannel(
                infoChannelId,
                "定时触发提醒",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "定时触发但无需执行时的静音留痕提醒"
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun granted(): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** 结果明细：成功显示广告进度与 VIP 到期；失败显示原因 */
    private fun detailOf(r: CheckinOutcome): String {
        val mark = if (r.success) "✓" else "✗"
        val tail = when {
            r.error.isNotEmpty() -> r.error
            r.vipEndTime.isNotEmpty() -> "VIP至 ${r.vipEndTime}"
            else -> "详见 APP"
        }
        return "$mark ${r.nickname}：广告 ${r.adClaimCount}/${r.adClaimTotal}，$tail"
    }

    /** 签到全部完成后的汇总通知 */
    fun notifyCheckinResult(results: List<CheckinOutcome>) {
        if (!granted() || results.isEmpty()) return
        val successCount = results.count { it.success }
        val failCount = results.size - successCount
        val title = when {
            failCount == 0 -> "KG签到：全部成功（${results.size} 个账号）"
            successCount == 0 -> "KG签到：全部失败（${results.size} 个账号）"
            else -> "KG签到完成：成功 $successCount 个，失败 $failCount 个"
        }
        val detail = results.joinToString("\n") { detailOf(it) }

        // 使用 launcher intent：APP 在后台时仅将其带回前台，不销毁重建 Activity
        //（避免签到页 ViewModel 重建导致用户正在看的日志列表被重置）
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_checkin)
            .setContentTitle(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFY_ID, notification)
    }

    /**
     * 定时触发但未实际执行（当日已签过 / 暂无账号 / 与APP内手动签到撞车）时的
     * 静音留痕提醒：这类场景服务瞬时结束，前台服务通知存活不足1秒肉眼不可见，
     * 补此通知让用户在通知栏即可确认“定时确实触发了”。同ID覆盖，重复投递只留最后一条。
     */
    fun notifySkipped(message: String) {
        if (!granted()) return
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, infoChannelId)
            .setSmallIcon(R.drawable.ic_stat_checkin)
            .setContentTitle("KG定时签到")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFY_INFO_ID, notification)
    }

    companion object {
        private const val NOTIFY_ID = 1001
        private const val NOTIFY_INFO_ID = 1003
    }
}