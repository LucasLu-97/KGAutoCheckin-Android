package com.kgcheckin.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kgcheckin.app.appContainer
import com.kgcheckin.app.schedule.CheckinScheduler
import com.kgcheckin.app.ui.AppRoot
import com.kgcheckin.app.ui.theme.KgCheckinTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var hasAskedNotification by mutableStateOf(false)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KgCheckinTheme {
                // Android 13+ 运行时通知权限（签到结果提醒）
                LaunchedEffect(Unit) {
                    if (!hasAskedNotification && Build.VERSION.SDK_INT >= 33) {
                        hasAskedNotification = true
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                AppRoot()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 打开APP自动对表（自愈保险）：漏触发、强停恢复、错过抽定时刻等断链场景，
        // 打开APP即按抽定时刻原值重排（幂等，可安全重复调用）；已错过且当天窗口
        // 未结束则在剩余窗口内补抽。跑进程级作用域，Activity 立刻退出也不会中断。
        applicationContext.appContainer.applicationScope.launch {
            runCatching { CheckinScheduler.ensureArmed(applicationContext, allowToday = true) }
        }
    }
}
