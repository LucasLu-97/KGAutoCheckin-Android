package com.kgcheckin.app.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgcheckin.app.data.CheckinRecord
import com.kgcheckin.app.ui.components.KugouAvatar
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** 截图2低饱和深灰蓝主色（仅作主题色未就绪时兼底色，正式色走 colorScheme） */
private val SlateBlueFallback = Color(0xFF47586E)

/** 内嵌模块容器色：夜间提高不透明度保证边界可见，日间保持柔和 */
@Composable
private fun moduleContainerColor(): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return MaterialTheme.colorScheme.surfaceVariant
}

/**
 * 签到日历页：
 * - 账号卡（头像+昵称+会员到期信息，三点菜单切换/管理账号）
 * - 月历：角标按钮 + 左右滑动手势切换月份（带滑动动画），行高按屏幕自适应舒展
 * - 下拉刷新：同步服务器领取记录与最新会员信息/头像
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    onManageAccounts: () -> Unit = {},
    vm: CalendarViewModel = viewModel(),
) {
    val accounts by vm.accounts.collectAsState()
    val selectedUserid by vm.selectedUserid.collectAsState()
    val month by vm.month.collectAsState()
    val records by vm.monthRecords.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val refreshMessage by vm.refreshMessage.collectAsState()

    LaunchedEffect(accounts.size) { vm.ensureSelection() }
    val account = accounts.firstOrNull { it.userid == selectedUserid }

    Scaffold(
        modifier = modifier,
        topBar = { com.kgcheckin.app.ui.components.CompactTitleBar("签到日历") },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 用屏幕实际高度动态计算月历行高：既舒展满屏，又保留可滚动容器（下拉刷新依赖）
            BoxWithConstraints {
                val availHeight = maxHeight
                val messageHeight = if (refreshMessage != null) 18.dp else 0.dp
                // 固定区域高度估算（账号卡/月头/表头/统计/间距/双卡内边距）
                val fixedHeight = 300.dp + messageHeight
                val rowHeight = ((availHeight - fixedHeight) / 6f)
                    .coerceIn(40.dp, 58.dp)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    refreshMessage?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))

                    // 卡片一：账号信息（独立模块，夜间仍保持可见边界）
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            AccountHeader(
                                accounts = accounts,
                                account = account,
                                selectedUserid = selectedUserid,
                                onSelectAccount = { vm.selectAccount(it) },
                                onManageAccounts = onManageAccounts,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // 卡片二：月历 + 打卡统计（独立模块）
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            MonthHeader(
                                month = month,
                                onPrev = { vm.prevMonth() },
                                onNext = { vm.nextMonth() },
                                onToday = { vm.goToday() },
                            )

                            // 月历：左右滑动切换月份（带滑动动画）
                            var dragTotal by remember { mutableFloatStateOf(0f) }
                            AnimatedContent(
                                targetState = month,
                                transitionSpec = {
                                    val forward = targetState > initialState
                                    if (forward) {
                                        (slideInHorizontally { it } + fadeIn()) togetherWith
                                            (slideOutHorizontally { -it } + fadeOut())
                                    } else {
                                        (slideInHorizontally { -it } + fadeIn()) togetherWith
                                            (slideOutHorizontally { it } + fadeOut())
                                    }
                                },
                                label = "month",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(Unit) {
                                        detectHorizontalDragGestures(
                                            onHorizontalDrag = { change, amount ->
                                                dragTotal += amount
                                                change.consume()
                                            },
                                            onDragEnd = {
                                                when {
                                                    dragTotal < -60f -> vm.nextMonth()
                                                    dragTotal > 60f -> vm.prevMonth()
                                                }
                                                dragTotal = 0f
                                            },
                                        )
                                    },
                            ) { m ->
                                MonthGrid(
                                    month = m,
                                    records = records,
                                    today = vm.today(),
                                    rowHeight = rowHeight,
                                )
                            }
                            Spacer(Modifier.height(6.dp))

                            StatsModule(
                                signedCount = records.count { it.value.success },
                                monthDays = month.lengthOfMonth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 账号卡：头像+昵称+会员到期信息；三点菜单内可切换账号 / 管理账号（添加/删除） */
@Composable
private fun AccountHeader(
    accounts: List<com.kgcheckin.app.data.Account>,
    account: com.kgcheckin.app.data.Account?,
    selectedUserid: String?,
    onSelectAccount: (String) -> Unit,
    onManageAccounts: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (account != null) {
            KugouAvatar(account = account, size = 46.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    account.displayNickname(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                // 展示模型：附加行（乐龄/听歌等）
                account.profileExtra()?.let { extra ->
                    Text(
                        extra,
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // VIP 明细行
                val vipRows = account.vipList
                val vipLines: List<String> = when {
                    vipRows.isNotEmpty() -> vipRows.take(2).map {
                        "${it.displayName} 剩余 ${it.remainDays} 天 · ${it.endTime}"
                    }
                    else -> listOf("暂无会员信息")
                }
                vipLines.take(2).forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        } else {
            Text(
                "请先添加账号",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }

        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.MoreVert,
                    "账号菜单",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                accounts.forEach { acc ->
                    DropdownMenuItem(
                        text = { Text(acc.displayNickname()) },
                        leadingIcon = {
                            KugouAvatar(account = acc, size = 26.dp)
                        },
                        trailingIcon = if (acc.userid == selectedUserid) {
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else null,
                        onClick = {
                            menuOpen = false
                            onSelectAccount(acc.userid)
                        },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DropdownMenuItem(
                    text = { Text("管理账号") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.ManageAccounts,
                            null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onManageAccounts()
                    },
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev, modifier = Modifier.size(34.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上个月", Modifier.size(20.dp))
        }
        Text(
            "${month.year} 年 ${month.monthValue} 月",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onNext, modifier = Modifier.size(34.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下个月", Modifier.size(20.dp))
        }
        TextButton(
            onClick = onToday,
            modifier = Modifier.height(32.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
        ) {
            Icon(Icons.Filled.Today, null, Modifier.size(13.dp))
            Spacer(Modifier.width(2.dp))
            Text("今天", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** 月历（周一开始）：行高由外部按屏幕高度传入，自适应舒展 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    records: Map<Long, CheckinRecord>,
    today: LocalDate,
    rowHeight: androidx.compose.ui.unit.Dp,
) {
    val firstDay = month.atDay(1)
    val leading = firstDay.dayOfWeek.value - 1 // 周一为第一列
    val daysInMonth = month.lengthOfMonth()
    val totalCells = leading + daysInMonth
    val rows = (totalCells + 6) / 7
    val circleSize = (rowHeight - 10.dp).coerceIn(30.dp, 36.dp)

    Column {
        // 星期表头
        Row(modifier = Modifier.fillMaxWidth().height(18.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(rows) { row ->
                Row(modifier = Modifier.fillMaxWidth().height(rowHeight)) {
                    repeat(7) { col ->
                        val cellIndex = row * 7 + col
                        val dayNumber = cellIndex - leading + 1
                        if (dayNumber in 1..daysInMonth) {
                            val date = month.atDay(dayNumber)
                            val record = records[date.toEpochDay()]
                            DayCell(
                                day = dayNumber,
                                isSigned = record?.success == true,
                                isToday = date == today,
                                isFuture = date.isAfter(today),
                                circleSize = circleSize,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** 单日格子：签到成功 = 深灰蓝实心圆 + 白字 + 右下角对勾小圆 */
@Composable
private fun DayCell(
    day: Int,
    isSigned: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    circleSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val contentColor = when {
        isSigned -> Color.White
        isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = if (isSigned) MaterialTheme.colorScheme.primary else Color.Transparent,
            border = if (isToday && !isSigned) {
                androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            } else null,
            modifier = Modifier.size(circleSize),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    day.toString(),
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSigned || isToday) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        if (isSigned) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 2.dp, end = 2.dp)
                    .size(11.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    null,
                    modifier = Modifier.size(8.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 打卡统计模块：环形进度 + 本月已打卡 N天 + 坚持中标签 */
@Composable
private fun StatsModule(signedCount: Int, monthDays: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = moduleContainerColor(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { if (monthDays > 0) signedCount.toFloat() / monthDays else 0f },
                    modifier = Modifier.size(36.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                    strokeWidth = 3.5.dp,
                    strokeCap = StrokeCap.Round,
                )
                Text(
                    "${if (monthDays > 0) (signedCount * 100 / monthDays) else 0}%",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "本月已打卡",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$signedCount",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        " / ${monthDays}天",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        null,
                        modifier = Modifier.size(12.dp),
                        tint = Color(0xFFD8722C),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "坚持中",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

private fun remainText(days: Int): String = when {
    days < 0 -> "已过期"
    days == 0 -> "今日到期"
    days < 30 -> "${days}天后到期"
    days < 365 -> "${days / 30}个月后到期"
    else -> "${days / 365}年后到期"
}
