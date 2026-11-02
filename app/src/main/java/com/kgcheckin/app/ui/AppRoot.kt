package com.kgcheckin.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgcheckin.app.KgApplication
import com.kgcheckin.app.data.CheckinSettings
import com.kgcheckin.app.ui.calendar.CalendarScreen
import com.kgcheckin.app.ui.checkin.CheckinScreen
import com.kgcheckin.app.ui.settings.SettingsScreen
import com.kgcheckin.app.ui.theme.KgCheckinTheme

/** 一级页面定义 */
private data class Tab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val tabs = listOf(
    Tab("签到领取", Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle),
    Tab("签到日历", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    Tab("更多设置", Icons.Filled.Settings, Icons.Outlined.Settings),
)

/** 应用根：主题（设置页实时切换）+ 紧凑底栏三页签 */
@Composable
fun AppRoot() {
    val container = (LocalContext.current.applicationContext as KgApplication).container
    val settings by container.settingsStore.settings.collectAsState(initial = CheckinSettings())

    KgCheckinTheme(
        themeMode = settings.themeMode,
        themeColor = settings.themeColor,
    ) {
        var currentTab by rememberSaveable { mutableIntStateOf(0) }

        Scaffold(
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            tabs.forEachIndexed { index, tab ->
                                val selected = currentTab == index
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { currentTab = index },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                        modifier = Modifier.size(if (selected) 24.dp else 22.dp),
                                        tint = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        tab.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            },
        ) { padding ->
            val tabModifier = Modifier.padding(padding)
            when (currentTab) {
                0 -> CheckinScreen(
                    onGoAccounts = { currentTab = 2 },
                    modifier = tabModifier,
                )
                1 -> CalendarScreen(
                    onManageAccounts = { currentTab = 2 },
                    modifier = tabModifier,
                )
                2 -> SettingsScreen(modifier = tabModifier)
            }
        }
    }
}
