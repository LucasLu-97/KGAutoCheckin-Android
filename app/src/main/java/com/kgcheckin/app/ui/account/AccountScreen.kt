package com.kgcheckin.app.ui.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgcheckin.app.data.Account
import com.kgcheckin.app.data.QrLoginState
import com.kgcheckin.app.ui.components.KugouAvatar

/**
 * 账号管理内容区（供「更多设置」页嵌入）：
 * 默认折叠为一行汇总（点击展开），避免多账号刷屏挤压其他设置项；
 * 无账号时始终展开以便直接添加。
 */
@Composable
fun AccountCardsSection(vm: AccountViewModel = viewModel()) {
    val accounts by vm.accounts.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val effectiveExpanded = expanded || accounts.isEmpty()

    Column {
        // 汇总行（点击展开/收起）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { expanded = !expanded }
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.AccountCircle,
                null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (accounts.isEmpty()) "暂无登录账号" else "已登录 ${accounts.size} 个账号 · 点击展开管理",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (effectiveExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                if (effectiveExpanded) "收起" else "展开",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (effectiveExpanded) {
            accounts.forEach { account ->
                AccountCard(
                    account = account,
                    onDelete = { vm.deleteAccount(account.userid) },
                )
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = { vm.openSheet() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            ) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("添加账号")
            }
        }
    }

    // 添加账号弹层
    AddAccountSheetHost(vm)
}

/** 添加账号底部弹层宿主 */
@Composable
fun AddAccountSheetHost(vm: AccountViewModel) {
    val sheetVisible by vm.sheetVisible.collectAsState()
    if (sheetVisible) {
        AddAccountSheet(vm)
    }
}

/** 账号卡片：头像+昵称+ID；会员类型与剩余天数、到期时间分行显示 */
@Composable
fun AccountCard(account: Account, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KugouAvatar(account = account, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    account.displayNickname(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "ID ${account.userid}",
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum").let {
                        MaterialTheme.typography.labelSmall.merge(it)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                account.profileExtra()?.let { extra ->
                    Text(
                        extra,
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val vipRows = account.vipList
                if (vipRows.isNotEmpty()) {
                    vipRows.take(2).forEach { row ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.WorkspacePremium,
                                null,
                                modifier = Modifier.size(13.dp),
                                tint = if (row.remainDays > 0) {
                                    Color(0xFFD8A318)
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (row.remainDays > 0) {
                                    "${row.displayName} · 剩余 ${row.remainDays} 天"
                                } else {
                                    "${row.displayName} · 已过期"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "至 ${row.endTime}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        "暂无会员信息，签到领取后展示",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, "删除账号", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除账号") },
            text = { Text("确定删除「${account.displayNickname()}」吗？其本地签到历史也将一并删除。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    confirmDelete = false
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

/** 添加账号弹层：扫码登录 / 验证码登录 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccountSheet(vm: AccountViewModel) {
    val qrMode by vm.qrMode.collectAsState()
    val qrState by vm.qrState.collectAsState()
    val smsMessage by vm.smsMessage.collectAsState()
    val smsCountdown by vm.smsCountdown.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mobile by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = { vm.closeSheet() },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("添加酷狗账号", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = qrMode,
                    onClick = { vm.switchMode(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Filled.QrCodeScanner, null, Modifier.size(18.dp)) },
                    label = { Text("扫码登录") },
                )
                SegmentedButton(
                    selected = !qrMode,
                    onClick = { vm.switchMode(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Filled.Sms, null, Modifier.size(18.dp)) },
                    label = { Text("验证码登录") },
                )
            }
            Spacer(Modifier.height(18.dp))

            if (qrMode) {
                QrLoginSection(qrState, onGenerate = { vm.startQrLogin() })
            } else {
                SmsLoginSection(
                    mobile = mobile,
                    code = code,
                    onMobileChange = { mobile = it },
                    onCodeChange = { code = it },
                    message = smsMessage,
                    countdown = smsCountdown,
                    onSendCode = { vm.sendSmsCode(mobile) },
                    onLogin = { vm.loginBySms(mobile, code) },
                )
            }
        }
    }
}

@Composable
private fun QrLoginSection(qrState: QrLoginState, onGenerate: () -> Unit) {
    when (qrState) {
        is QrLoginState.Idle -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "使用「酷狗音乐 APP」扫描二维码完成登录\n二维码约 2 分钟有效",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                Button(onClick = onGenerate) { Text("生成二维码") }
            }
        }
        is QrLoginState.Loading -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("正在生成二维码 ...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        is QrLoginState.Ready -> {
            QrImage(qrState.bitmap)
            Spacer(Modifier.height(12.dp))
            Text(
                "请打开酷狗音乐 APP 扫一扫，扫码后请在手机上确认登录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onGenerate) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("刷新二维码")
            }
        }
        is QrLoginState.Scanned -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(30.dp))
                Icon(
                    Icons.Filled.Verified, null,
                    tint = Color(0xFF2E9E5B),
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text("已扫码", fontWeight = FontWeight.SemiBold)
                Text(
                    "请在酷狗音乐 APP 上点击确认登录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(30.dp))
            }
        }
        is QrLoginState.Expired -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("二维码已过期", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onGenerate) { Text("重新生成二维码") }
            }
        }
        is QrLoginState.Success -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Verified, null,
                    tint = Color(0xFF2E9E5B),
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text("登录成功：${qrState.account.displayNickname()}", fontWeight = FontWeight.SemiBold)
                Text(
                    "登录信息已加密保存在本机",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onGenerate) { Text("继续添加下一个账号") }
            }
        }
        is QrLoginState.Error -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(qrState.message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onGenerate) { Text("重试") }
            }
        }
    }
}

/** 扫码登录二维码图片 */
@Composable
private fun QrImage(bitmap: android.graphics.Bitmap) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "登录二维码",
            modifier = Modifier.size(250.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun SmsLoginSection(
    mobile: String,
    code: String,
    onMobileChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    message: String,
    countdown: Int,
    onSendCode: () -> Unit,
    onLogin: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = mobile,
            onValueChange = { if (it.length <= 11 && it.all { c -> c.isDigit() }) onMobileChange(it) },
            label = { Text("手机号") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onCodeChange(it) },
            label = { Text("短信验证码") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                TextButton(
                    onClick = onSendCode,
                    enabled = countdown == 0 && mobile.length == 11,
                ) {
                    Text(if (countdown > 0) "${countdown}s" else "发送验证码")
                }
            },
        )
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = mobile.length == 11 && code.length >= 4,
        ) { Text("登录") }
        Spacer(Modifier.height(6.dp))
        Text(
            "注：一个手机号绑定多个酷狗账号时无法用验证码登录，请改用扫码方式",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
