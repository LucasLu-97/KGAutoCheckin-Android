package com.kgcheckin.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kgcheckin.app.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 开机恢复：重建每日定时闹钟（闹钟在重启后会被系统清空）。
 * 注意：仅重排闹钟，不直接启动前台服务（Android 15+ 禁止开机自启 dataSync 服务）。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val settings = app.appContainer.settingsStore.settings.first()
                if (settings.autoEnabled) {
                    CheckinScheduler.schedule(app, settings.hour, settings.minute)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
