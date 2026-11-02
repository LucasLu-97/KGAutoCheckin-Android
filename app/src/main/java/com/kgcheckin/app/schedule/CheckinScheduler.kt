package com.kgcheckin.app.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * 每日定时闹钟管理（方案A核心）。
 *
 * - Android 12+ 使用 setExactAndAllowWhileIdle 精确闹钟（可在 Doze 中触发），
 *   未授权精确闹钟时自动降级为 setAndAllowWhileIdle（误差可能几分钟）
 * - 触发时刻由 AlarmReceiver 拉起签到前台服务（精确闹钟属于后台启动前台服务的系统豁免场景）
 */
object CheckinScheduler {

    private const val REQUEST_CODE = 2001

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 当前是否允许精确闹钟（Android 12-13 默认允许；Android 14+ 需用户授权） */
    fun canExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return runCatching {
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        }.getOrDefault(false)
    }

    /** 下一次执行时刻（今天 hh:mm 已过则取明天） */
    fun nextRun(hour: Int, minute: Int): Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    /** 排定下一次（及后续每日，服务完成后自动续排） */
    fun schedule(context: Context, hour: Int, minute: Int) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = nextRun(hour, minute).timeInMillis
        val pi = pendingIntent(context)
        if (canExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }
}
