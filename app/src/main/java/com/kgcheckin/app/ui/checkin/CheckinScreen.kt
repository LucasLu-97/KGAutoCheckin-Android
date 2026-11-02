package com.kgcheckin.app.ui.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.buildAnnotatedString
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgcheckin.app.core.LogEvent
import com.kgcheckin.app.core.LogLevel
import com.kgcheckin.app.data.CheckinCoordinator
import com.kgcheckin.app.ui.components.KugouAvatar

// 日志终端配色：浅色（截图1 GitHub Actions 风格）
private val LogBgLight = Color(0xFFF6F8FA)
private val LineNoColorLight = Color(0xFF8C959F)
private val ColorInfoLight = Color(0xFF3C4043)
private val ColorAccountLight = Color(0xFF8250DF)
private val ColorStepLight = Color(0xFF954B16)
private val ColorSuccessLight = Color(0xFF1A7F37)
private val ColorDatetimeLight = Color(0xFF0969DA)
private val ColorWarnLight = Color(0xFFB45309)
private val ColorErrorLight = Color(0xFFCF222E)

// 日志终端配色：夜间（低亮度不刺眼）
private val LogBgDark = Color(0xFF151A22)
private val LineNoColorDark = Color(0xFF76808F)
private val ColorInfoDark = Color(0xFFC6CDD8)
private val ColorAccountDark = Color(0xFFB18CF0)
private val ColorStepDark = Color(0xFFD89A6A)
private val ColorSuccessDark = Color(0xFF6FD48F)
private val ColorDatetimeDark = Color(0xFF6FB4F0)
private val ColorWarnDark = Color(0xFFE5B96A)
private val ColorErrorDark = Color(0xFFF07E72)

/** 当前深浅模式下的终端配色 */
private data class TerminalColors(
    val bg: Color,
    val lineNo: Color,
    val info: Color,
    val account: Color,
    val step: Color,
    val success: Color,
    val datetime: Color,
    val warn: Color,
    val error: Color,
)

@Composable
private fun terminalColors(): TerminalColors {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dark) {
        TerminalColors(
            LogBgDark, LineNoColorDark, ColorInfoDark, ColorAccountDark,
            ColorStepDark, ColorSuccessDark, ColorDatetimeDark, ColorWarnDark, ColorErrorDark,
        )
    } else {
        TerminalColors(
            LogBgLight, LineNoColorLight, ColorInfoLight, ColorAccountLight,
            ColorStepLight, ColorSuccessLight, ColorDatetimeLight, ColorWarnLight, ColorErrorLight,
        )
    }
}

/** 签到领取页：头像选账号 → 开始签到 → 实时过程日志（含执行时间，可清除） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckinScreen(
    onGoAccounts: () -> Unit,
    modifier: Modifier = Modifier,
    vm: CheckinViewModel = viewModel(),
) {
    val accounts by vm.accounts.collectAsState()
    val selected by vm.selected.collectAsState()
    val logs by vm.logs.collectAsState()
    val running by vm.running.collectAsState()
    val lastSummary by vm.lastSummary.collectAsState()
    val serviceRunning by CheckinCoordinator.running.collectAsState()

    // 打开即签（方案B）：进入页面即检查
    LaunchedEffect(Unit) { vm.maybeAutoStartOnOpen() }

    Scaffold(
        modifier = modifier,
        topBar = { com.kgcheckin.app.ui.components.CompactTitleBar("签到领取") },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            if (accounts.isEmpty()) {
                EmptyAccountsCard(onGoAccounts)
            } else {
                // 账号选择：头像方块（不选 = 全部账号），长昵称小字省略截断
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    accounts.forEach { account ->
                        val checked = selected.contains(account.userid)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(50.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { vm.toggleSelect(account.userid) }
                                .padding(vertical = 2.dp),
                        ) {
                            KugouAvatar(
                                account = account,
                                size = 42.dp,
                                selected = checked,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                account.displayNickname(),
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (checked) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 开始 / 取消按钮
            Button(
                onClick = { if (running) vm.cancel() else vm.start() },
                enabled = !serviceRunning || running,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = if (running) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                if (running) {
                    // 取消状态不转圈，避免多元素堆叠
                    Icon(Icons.Filled.Cancel, null)
                    Spacer(Modifier.width(6.dp))
                    Text("取消签到", fontSize = 16.sp)
                } else {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("开始签到", fontSize = 16.sp)
                }
            }

            // 结果/服务状态提示
            val notice = lastSummary
                ?: if (serviceRunning && !running) "定时服务正在后台签到，可在通知栏查看进度" else null
            notice?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(8.dp))

            // 实时执行过程日志（当日历次执行都保留，带执行时间）
            LogTerminal(
                logs = logs,
                running = running,
                working = running || serviceRunning,
                onClearLogs = { vm.clearLogs() },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EmptyAccountsCard(onGoAccounts: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "还没有登录酷狗账号",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "添加账号后即可手动一键签到，每天领取概念 VIP",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onGoAccounts) { Text("去添加账号") }
        }
    }
}

// ═══════════════════════ 终端日志区 ═══════════════════════

/** 终端风格日志区：时间前缀+彩色消息；执行中在标题后转圈提示，可翻阅历史、一键回底、一键清除 */
@Composable
private fun LogTerminal(
    logs: List<LogEvent>,
    running: Boolean,
    working: Boolean,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = terminalColors()
    val listState = rememberLazyListState()

    // 新日志到达时：仅在用户位于（或接近）底部时自动跟随，避免打断向上翻阅
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (lastVisible >= logs.size - 3) {
                runCatching { listState.animateScrollToItem(logs.lastIndex) }
            }
        }
    }
    val showJump by remember {
        derivedStateOf { listState.canScrollForward }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.bg,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box {
            Column {
                // 标题栏：状态图标 + 执行签到 + 条数 + 清除
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (running) {
                        // 运行中行首保持灰点，转圈仅保留"执行签到"后一处
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(colors.lineNo.copy(alpha = 0.5f), CircleShape),
                        )
                    } else if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(colors.lineNo.copy(alpha = 0.5f), CircleShape),
                        )
                    } else {
                        Icon(
                            Icons.Filled.CheckCircle,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.success,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "执行签到",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (working) {
                        Spacer(Modifier.width(6.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.8.dp,
                        )
                        // 运行计时：每秒刷新，结束后停在固定耗时
                        Spacer(Modifier.width(4.dp))
                        val startedAt = CheckinCoordinator.startedAt
                        var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
                        LaunchedEffect(startedAt) {
                            while (startedAt > 0) {
                                nowMs = System.currentTimeMillis()
                                delay(500)
                            }
                        }
                        if (startedAt > 0) {
                            Text(
                                formatElapsed(nowMs - startedAt),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = colors.lineNo,
                            )
                        }
                    } else if (CheckinCoordinator.lastDurationMs > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "耗时 " + formatElapsed(CheckinCoordinator.lastDurationMs),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = colors.lineNo,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (logs.isNotEmpty()) {
                        Text(
                            "${logs.size} 条",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.lineNo,
                        )
                        Surface(
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = Modifier.padding(start = 2.dp),
                        ) {
                            IconButton(
                                onClick = onClearLogs,
                                modifier = Modifier.size(26.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.DeleteSweep,
                                    "清除日志",
                                    modifier = Modifier.size(15.dp),
                                    tint = colors.lineNo,
                                )
                            }
                        }
                    }
                }

                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "开始签到后实时展示执行过程",
                            color = colors.lineNo,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                } else {
                    // 可自由长按选择复制日志文本（便于反馈字段清单）
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            items(logs) { event ->
                                LogLine(event)
                            }
                        }
                    }
                }
            }

            // 回到底部悬浮按钮（翻阅历史时出现）
            if (showJump && logs.isNotEmpty()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                ) {
                    IconButton(
                        onClick = {
                            if (logs.isNotEmpty()) {
                                listState.requestScrollToItem(logs.lastIndex)
                            }
                        },
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            "回到底部",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 毫秒 → “XmYs” 文本（超1小时显示 XhYm） */
private fun formatElapsed(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%dh%02dm".format(h, m) else "%dm%02ds".format(m, s)
}
/** 单行日志：时间前缀（灰色）与消息（彩色）合为单行富文本，时间永不折行 */
@Composable
private fun LogLine(event: LogEvent) {
    val colors = terminalColors()
    val messageColor = when (event.level) {
        LogLevel.INFO -> colors.info
        LogLevel.ACCOUNT -> colors.account
        LogLevel.STEP -> colors.step
        LogLevel.SUCCESS -> colors.success
        LogLevel.DATETIME -> colors.datetime
        LogLevel.WARN -> colors.warn
        LogLevel.ERROR -> colors.error
    }
    Text(
        buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = colors.lineNo,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            ) {
                append(event.timeText)
                append("  ")
            }
            withStyle(
                SpanStyle(
                    color = messageColor,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            ) {
                append(event.message)
            }
        },
        modifier = Modifier.padding(vertical = 1.dp),
    )
}
