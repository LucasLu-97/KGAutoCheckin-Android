package com.kgcheckin.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 开机恢复：重建每日定时闹钟（闹钟在重启后会被系统清空）。
 * 按已抽定的随机时刻原样重排（不重新抽）；若错过抽定时刻且当天窗口未结束，
 * 在剩余窗口内补抽一个新随机点，尽量保住当天的签到。
 * 注意：仅重排闹钟，不直接启动前台服务（Android 15+ 禁止开机自启 dataSync 服务）。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                runCatching { CheckinScheduler.ensureArmed(app, allowToday = true) }
            } finally {
                // goAsync 理论上非空，防御个别 ROM 下的边界场景，避免开机路径 NPE
                pendingResult?.finish()
            }
        }
    }
}
