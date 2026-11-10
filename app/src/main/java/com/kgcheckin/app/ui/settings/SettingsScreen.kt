package com.kgcheckin.app.ui.settings

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgcheckin.app.BuildConfig
import com.kgcheckin.app.schedule.CheckinScheduler
import com.kgcheckin.app.ui.account.AccountCardsSection
import com.kgcheckin.app.ui.theme.THEME_OPTIONS
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ColorSuccess = Color(0xFF1A7F37)

/** 更多设置页：签到自动化 / 定时可靠性 / 关于 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier, vm: SettingsViewModel = viewModel()) {
    val settings by vm.settings.collectAsState()
    val updateState by vm.updateState.collectAsState()
    var showWindowStartPicker by remember { mutableStateOf(false) }
    var showWindowEndPicker by remember { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf(0 to 0) }
    var windowPickerError by remember { mutableStateOf<String?>(null) }
    var showGuide by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { com.kgcheckin.app.ui.components.CompactTitleBar("更多设置") },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            // 账号管理（原账户登录页并入）
            SectionLabel("账号管理")
            AccountCardsSection()

            SectionLabel("主题")
            SettingsCard {
                // 深浅模式：跟随系统 / 日间 / 夜间
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("深浅模式", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    listOf(0 to "跟随系统", 1 to "日间", 2 to "夜间").forEach { (mode, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { vm.setThemeMode(mode) },
                            label = { androidx.compose.material3.Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(start = 4.dp).height(30.dp),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                // 主题色：壁纸取色 + 内置莫奈色
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text("主题色", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        THEME_OPTIONS.forEach { option ->
                            val selected = settings.themeColor == option.key
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box {
                                    Surface(
                                        shape = CircleShape,
                                        color = option.lightColor,
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) { vm.setThemeColor(option.key) },
                                    ) {}
                                    if (selected) {
                                        // 选中标识：白色对勾 + 主题色描边环
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Transparent,
                                            border = androidx.compose.foundation.BorderStroke(
                                                2.dp,
                                                MaterialTheme.colorScheme.primary,
                                            ),
                                            modifier = Modifier.size(30.dp),
                                        ) {}
                                        androidx.compose.material3.Icon(
                                            Icons.Filled.Check,
                                            null,
                                            tint = Color.White,
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(14.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    option.label,
                                    style = MaterialTheme.typography.labelSmall,
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

            SectionLabel("签到自动化")
            SettingsCard {
                SettingSwitchRow(
                    title = "每日定时自动签到",
                    desc = if (settings.autoEnabled) {
                        if (settings.nextPickEpochMillis > 0L) {
                            "下次 " + nextPickText(settings.nextPickEpochMillis) + "（窗口内随机）"
                        } else {
                            "已开启，每天在窗口内随机执行"
                        }
                    } else {
                        "每天在时间窗口内随机抽一个时刻，后台静默签到全部账号"
                    },
                    checked = settings.autoEnabled,
                    onChange = { vm.setAutoEnabled(it) },
                )
                if (settings.autoEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("签到时间窗口", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = { showWindowStartPicker = true },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                        ) {
                            Text(
                                "%02d:%02d - %02d:%02d".format(
                                    settings.windowStartHour,
                                    settings.windowStartMinute,
                                    settings.windowEndHour,
                                    settings.windowEndMinute,
                                ),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingSwitchRow(
                    title = "打开APP即签到",
                    desc = "进APP自动补签今日未签账号",
                    checked = settings.openToCheckin,
                    onChange = { vm.setOpenToCheckin(it) },
                )
            }

            SectionLabel("定时可靠性")
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("后台权限设置", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "自启动 / 电池无限制 / 精确闹钟",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { showGuide = true }) { Text("打开") }
                }
            }

            SectionLabel("数据管理")
            SettingsCard {
                var confirmReset by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("重置应用数据", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "清除签到历史、日志与设置，保留账号",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { confirmReset = true }) {
                        Text("重置", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (confirmReset) {
                    var done by remember { mutableStateOf(false) }
                    AlertDialog(
                        onDismissRequest = { if (!done) confirmReset = false },
                        title = { Text("重置应用数据") },
                        text = {
                            if (done) {
                                Text("已重置\n账号已保留，其余全部清空")
                            } else {
                                Text("清除签到记录、日志与设置，仅保留账号登录\n确定继续？")
                            }
                        },
                        confirmButton = {
                            if (done) {
                                TextButton(onClick = { confirmReset = false }) { Text("完成") }
                            } else {
                                TextButton(onClick = {
                                    vm.resetAllData { done = true }
                                }) { Text("确定", color = MaterialTheme.colorScheme.error) }
                            }
                        },
                        dismissButton = {
                            if (!done) {
                                TextButton(onClick = { confirmReset = false }) { Text("取消") }
                            }
                        },
                    )
                }
            }

            SectionLabel("关于")
            SettingsCard {
                AboutClickRow(
                    key = "检查更新",
                    value = BuildConfig.VERSION_NAME,
                ) { vm.checkUpdate(BuildConfig.VERSION_NAME) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                val context = LocalContext.current
                AboutClickRow(
                    key = "开源仓库",
                    value = "LucasLu-97/KGAutoCheckin-Android",
                ) { openUrl(context, "https://github.com/LucasLu-97/KGAutoCheckin-Android") }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // 窗口选择第一步：开始时间
    if (showWindowStartPicker) {
        TimePickerDialog(
            title = "窗口开始时间",
            initialHour = settings.windowStartHour,
            initialMinute = settings.windowStartMinute,
            onConfirm = { h, m ->
                pendingStart = h to m
                windowPickerError = null
                showWindowStartPicker = false
                showWindowEndPicker = true
            },
            onDismiss = { showWindowStartPicker = false },
        )
    }

    // 窗口选择第二步：结束时间（需晚于开始，暂不支持跨午夜）
    if (showWindowEndPicker) {
        val (startH, startM) = pendingStart
        val currentEnd = settings.windowEndHour * 60 + settings.windowEndMinute
        val initEnd = if (currentEnd > startH * 60 + startM) {
            settings.windowEndHour to settings.windowEndMinute
        } else {
            val candidate = minOf(startH * 60 + startM + 60, 23 * 60 + 59)
            candidate / 60 to candidate % 60
        }
        TimePickerDialog(
            title = "窗口结束时间",
            initialHour = initEnd.first,
            initialMinute = initEnd.second,
            supportingText = windowPickerError,
            onConfirm = { h, m ->
                if (h * 60 + m <= startH * 60 + startM) {
                    windowPickerError = "结束时间需晚于开始时间（暂不支持跨午夜）"
                } else {
                    windowPickerError = null
                    vm.setAutoWindow(startH, startM, h, m)
                    showWindowEndPicker = false
                }
            },
            onDismiss = {
                showWindowEndPicker = false
                windowPickerError = null
            },
        )
    }

    if (showGuide) {
        PermissionsGuideDialog(onDismiss = { showGuide = false })
    }

    // 版本检查弹窗
    val context = LocalContext.current
    when (val s = updateState) {
        is UpdateState.Checking -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("检查更新") },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("正在检查 GitHub 最新版本 ...")
                    }
                },
                confirmButton = {},
            )
        }
        is UpdateState.UpToDate -> {
            AlertDialog(
                onDismissRequest = { vm.dismissUpdate() },
                title = { Text("版本检查") },
                text = { Text("已是最新版本 v${s.current}（GitHub Release）") },
                confirmButton = {
                    TextButton(onClick = { vm.dismissUpdate() }) { Text("确定") }
                },
            )
        }
        is UpdateState.Available -> {
            AlertDialog(
                onDismissRequest = { vm.dismissUpdate() },
                title = { Text("发现新版本 v${s.latest}") },
                text = {
                    Text(
                        "当前 v${s.current} → 最新 v${s.latest}\n\n" +
                            "点击「去下载」将在浏览器打开并下载与手机架构匹配的安装包",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        openUrl(context, s.downloadUrl)
                        vm.dismissUpdate()
                    }) { Text("去下载") }
                },
                dismissButton = {
                    TextButton(onClick = { vm.dismissUpdate() }) { Text("取消") }
                },
            )
        }
        is UpdateState.Failed -> {
            AlertDialog(
                onDismissRequest = { vm.dismissUpdate() },
                title = { Text("版本检查") },
                text = { Text("检查失败：${s.message}\n请稍后重试") },
                confirmButton = {
                    TextButton(onClick = { vm.dismissUpdate() }) { Text("确定") }
                },
            )
        }
        UpdateState.Idle -> Unit
    }
}

// ═══════════════════════ 组件 ═══════════════════════

/**
 * 分区标题：主流设置页规范——安静的小灰字，仅作分组分隔；
 * 层级：22sp页标题 > 16sp设置项标题（正文主角） > 12sp分区标题/说明
 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 可点击的关于行 */
@Composable
private fun AboutClickRow(key: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(key, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 用浏览器打开 URL */
private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun nextPickText(pickEpochMillis: Long): String =
    SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(pickEpochMillis))

/** 时间选择弹窗（标题可定制，支持底部错误提示文案） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    supportingText: String? = null,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                TimePicker(state = state)
                if (supportingText != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        supportingText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 定时可靠性设置弹窗：图标行布局，已完成项显示对勾 */
@Composable
private fun PermissionsGuideDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pm = remember { context.getSystemService(PowerManager::class.java) }
    val ignoringBattery = remember { pm.isIgnoringBatteryOptimizations(context.packageName) }
    val needExactAlarm = remember {
        Build.VERSION.SDK_INT >= 31 && !CheckinScheduler.canExact(context)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("定时可靠性设置", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GuideRow(
                    icon = { Icon(Icons.Outlined.Autorenew, null, tint = MaterialTheme.colorScheme.primary) },
                    title = "允许自启动",
                    desc = "手机管家 → 自启动",
                    actionText = "打开",
                    onAction = { openMiuiAutostart(context) },
                )
                GuideRow(
                    icon = { Icon(Icons.Outlined.Bolt, null, tint = MaterialTheme.colorScheme.primary) },
                    title = "电池策略无限制",
                    desc = "省电策略设为无限制",
                    actionText = if (ignoringBattery) null else "去设置",
                    done = ignoringBattery,
                    onAction = { requestBatteryUnrestricted(context) },
                )
                if (needExactAlarm) {
                    GuideRow(
                        icon = { Icon(Icons.Outlined.Alarm, null, tint = MaterialTheme.colorScheme.primary) },
                        title = "允许精确闹钟",
                        desc = "不授权会有几分钟误差",
                        actionText = "去授权",
                        onAction = { requestExactAlarm(context) },
                    )
                }
                GuideRow(
                    icon = { Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary) },
                    title = "锁定后台",
                    desc = "最近任务下拉锁定",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.TipsAndUpdates,
                        null,
                        Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "备用：手机「自动任务」每天定时打开本APP，也能自动签到",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

/** 设置项行：圆形图标容器 + 标题/说明 + 右侧操作（已完成显示对勾），统一对齐与间距 */
@Composable
private fun GuideRow(
    icon: @Composable () -> Unit,
    title: String,
    desc: String,
    actionText: String? = null,
    done: Boolean = false,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (done) Modifier.padding(0.dp) else Modifier.padding(0.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            // 图标容器统一配色（含已完成项），完成状态由右侧绿勾表达
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { icon() }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(1.dp))
            Text(
                desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (done) {
            Box(
                modifier = Modifier.size(width = 64.dp, height = 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    "已完成",
                    modifier = Modifier.size(20.dp),
                    tint = ColorSuccess,
                )
            }
        } else if (actionText != null) {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 36.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onAction() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    actionText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ═══════════════════════ 系统设置页跳转 ═══════════════════════

/** 直达小米自启动管理页（失败则回退应用详情） */
private fun openMiuiAutostart(context: Context) {
    val candidates = listOf(
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity",
        ),
    )
    for (component in candidates) {
        try {
            context.startActivity(
                Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        } catch (_: Exception) {
        }
    }
    openAppDetails(context)
}

@SuppressLint("BatteryLife")
private fun requestBatteryUnrestricted(context: Context) {
    try {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
            openAppDetails(context)
        }
    }
}

private fun requestExactAlarm(context: Context) {
    try {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: Exception) {
        openAppDetails(context)
    }
}

private fun openAppDetails(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
