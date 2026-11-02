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
import com.kgcheckin.app.ui.AppRoot
import com.kgcheckin.app.ui.theme.KgCheckinTheme

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
}
