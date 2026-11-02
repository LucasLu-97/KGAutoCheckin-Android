package com.kgcheckin.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.kgcheckin.app.data.Account

/**
 * 头像缓存戳：下拉刷新时自增，使头像 URL 变化以绕过图片缓存强制重新拉取。
 * Coil 自带内存+磁盘缓存，默认使用缓存；手动刷新时才重新下载。
 */
object AvatarCache {
    var stamp by mutableLongStateOf(0L)
        private set

    fun bump() {
        stamp = System.currentTimeMillis()
    }
}

private fun stampedUrl(url: String): String {
    val s = AvatarCache.stamp
    if (s == 0L) return url
    return if (url.contains('?')) "$url&_t=$s" else "$url?_t=$s"
}

/**
 * 酷狗账号头像：优先网络头像（Coil），加载中/失败回落昵称首字符圆形；
 * 选中态加主色描边 + 右下角对勾角标。
 */
@Composable
fun KugouAvatar(
    account: Account,
    size: Dp,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    fontSize: TextUnit? = null,
) {
    val fallbackChar = account.nickname.trim().takeIf { it.isNotEmpty() }
        ?: account.userid.takeLast(1).takeIf { it.isNotEmpty() }
        ?: "?"

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (account.pic.isNotEmpty()) {
            SubcomposeAsyncImage(
                model = stampedUrl(account.pic),
                contentDescription = "${account.displayNickname()}的头像",
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .then(
                        if (selected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier
                        }
                    ),
                contentScale = ContentScale.Crop,
                loading = { AvatarFallback(fallbackChar, size, fontSize, selected) },
                error = { AvatarFallback(fallbackChar, size, fontSize, selected) },
            )
        } else {
            AvatarFallback(fallbackChar, size, fontSize, selected)
        }

        if (selected) {
            // 右下角选中角标
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size / 3.6f)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    null,
                    modifier = Modifier.size(size / 6.5f),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/** 首字符圆形兜底头像 */
@Composable
private fun AvatarFallback(char: String?, size: Dp, fontSize: TextUnit?, selected: Boolean) {
    val resolved = fontSize ?: (size.value * 0.4f).sp
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .size(size)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                }
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(size),
        ) {
            Text(
                char ?: "",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = resolved,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
