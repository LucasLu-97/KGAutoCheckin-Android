package com.kgcheckin.app.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kgcheckin.app.appContainer
import kotlinx.coroutines.flow.first
import java.util.Calendar
import kotlin.random.Random

/**
 * 每日定时闹钟管理（方案A核心，时间窗口随机版）。
 *
 * - 每天在用户选定的时间窗口内随机抽一个执行时刻，打散"每天同一时刻"的可疑规律
 * - 抽定的时刻持久化在 DataStore：手机重启、打开APP时按原值恢复闹钟，不重新抽
 * - Android 12+ 使用 setExactAndAllowWhileIdle 精确闹钟（可在 Doze 中触发），
 *   未授权精确闹钟时自动降级为 setAndAllowWhileIdle（误差可能几分钟）
 * - 触发时刻由 AlarmReceiver 拉起签到前台服务（精确闹钟属于后台启动前台服务的系统豁免场景）
 * - 闹钟是一次性的，每次执行完后由 CheckinService 续排下一个随机时刻（接力链）；
 *   App 被划掉不影响已排闹钟，重启由 BootReceiver 恢复，漏触发由打开APP自动对表自愈
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

    /**
     * 统一重排入口（幂等，可安全重复调用）：
     * 1) 自动签到未开启 → 什么都不做（开关关闭路径由调用方负责 cancel）
     * 2) 已抽定且未过期的时刻 → 按原值重排，不重新抽（保证开机/打开APP后与之前一致）
     * 3) 时刻已过期或尚未抽定 → 按窗口规则重新抽定并持久化
     *
     * @param allowToday true=允许在"今天剩余窗口"内补抽（开关开启/改窗口/开机/打开APP场景，
     * 错过抽定时刻也能当天补上）；false=只考虑起始时刻在当前之后的窗口（签到执行完后的
     * 续排场景，避免当天窗口已被消耗后再排出一个无效闹钟）
     */
    suspend fun ensureArmed(context: Context, allowToday: Boolean) {
        val store = context.appContainer.settingsStore
        val s = store.settings.first()
        if (!s.autoEnabled) return
        val now = System.currentTimeMillis()
        val at = s.nextPickEpochMillis.takeIf { it > now } ?: run {
            val epoch = nextPick(
                s.windowStartHour, s.windowStartMinute,
                s.windowEndHour, s.windowEndMinute,
                allowToday,
            ).timeInMillis
            store.setNextPick(epoch)
            epoch
        }
        register(context, at)
    }

    /**
     * 计算下一次随机执行时刻（纯函数；秒/毫秒归零，分钟粒度）：
     * - 窗口非法（结束不晚于开始，含跨午夜）→ 兜底为窗口起点整点，保证定时仍然存在
     * - allowToday=true：今天窗口还有剩余 → 在 [max(起点,当前+1分), 结束) 内随机；
     *   否则随机明天 [起点, 结束)
     * - allowToday=false：只排"起始时刻在当前之后"的最近一次窗口——常规场景即明天；
     *   跨午夜执行完（如 23:55 触发、00:04 跑完）的场景则是当晚尚未开始的窗口
     */
    fun nextPick(
        startHour: Int, startMinute: Int,
        endHour: Int, endMinute: Int,
        allowToday: Boolean,
        now: Calendar = Calendar.getInstance(),
    ): Calendar {
        val nowMinuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val start = (startHour * 60 + startMinute).coerceIn(0, 23 * 60 + 59)
        val end = (endHour * 60 + endMinute).coerceIn(0, 23 * 60 + 59)
        if (end <= start) {
            // 兜底：非法窗口按起点整点执行（今天起点已过则明天）
            return atMinute(now, start, if (allowToday && start > nowMinuteOfDay) 0 else 1)
        }
        return if (allowToday) {
            val lo = maxOf(start, nowMinuteOfDay + 1)
            if (lo < end) randomAtMinute(now, lo, end, 0) else randomAtMinute(now, start, end, 1)
        } else {
            if (start > nowMinuteOfDay) {
                randomAtMinute(now, start, end, 0)
            } else {
                randomAtMinute(now, start, end, 1)
            }
        }
    }

    /** [loMinuteOfDay, hiMinuteOfDay) 内均匀随机取一个分钟点，落在 now 起偏移 dayOffset 天的那一天 */
    private fun randomAtMinute(now: Calendar, loMinuteOfDay: Int, hiMinuteOfDay: Int, dayOffset: Int): Calendar {
        val picked = loMinuteOfDay + Random.nextInt(hiMinuteOfDay - loMinuteOfDay)
        return atMinute(now, picked, dayOffset)
    }

    private fun atMinute(now: Calendar, minuteOfDay: Int, dayOffset: Int): Calendar =
        (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    /** 注册一次性闹钟（同一 PendingIntent 重复注册等价于覆盖更新，天然幂等） */
    private fun register(context: Context, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
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
