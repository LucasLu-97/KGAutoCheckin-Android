package com.kgcheckin.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kgcheckin.app.service.CheckinService

/** 定时闹钟触发：拉起签到前台服务（精确闹钟属于后台启动前台服务的系统豁免场景） */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_FIRE) {
            CheckinService.start(context.applicationContext)
        }
    }

    companion object {
        const val ACTION_FIRE = "com.kgcheckin.app.action.CHECKIN_ALARM"
    }
}
