package com.kgcheckin.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** 内置主题色定义（高级莫兰迪色板） */
data class ThemeOption(
    val key: String,
    val label: String,
    val lightColor: Color,
    val darkColor: Color,
)

/** 可选主题色：随壁纸取色 + 7 套高级莫兰迪色 */
val THEME_OPTIONS = listOf(
    ThemeOption("dynamic", "壁纸取色", Color(0xFF6750A4), Color(0xFFD0BCFF)),
    ThemeOption("ink", "黛蓝", Color(0xFF3A5A8C), Color(0xFFA9C0E8)),
    ThemeOption("graphite", "石墨", Color(0xFF566270), Color(0xFFA8B2BA)),
    ThemeOption("sage", "松烟", Color(0xFF6B8F6E), Color(0xFFB5D0B8)),
    ThemeOption("mauve", "暮紫", Color(0xFF8B6FA8), Color(0xFFC9B2E0)),
    ThemeOption("honey", "蜜茶", Color(0xFFA87C4F), Color(0xFFE0C9A0)),
    ThemeOption("blush", "绯樱", Color(0xFFB56576), Color(0xFFEFAFB9)),
    ThemeOption("verdigris", "苍青", Color(0xFF4E7E74), Color(0xFFA3CCC3)),
)

private fun lightSchemeFor(seedLight: Color): androidx.compose.material3.ColorScheme {
    // 表面色全部由种子色微染派生，保证选任何主题色时底色与板卡协调统一
    return lightColorScheme(
        primary = seedLight,
        onPrimary = Color.White,
        primaryContainer = seedLight.copy(alpha = 0.18f).compositeOn(Color.White),
        onPrimaryContainer = Color(0xFF10141C),
        secondary = seedLight.copy(alpha = 0.85f),
        secondaryContainer = seedLight.copy(alpha = 0.16f).compositeOn(Color.White),
        onSecondaryContainer = Color(0xFF10141C),
        tertiary = seedLight,
        error = Color(0xFFBA1A1A),
        background = seedLight.copy(alpha = 0.05f).compositeOn(Color(0xFFF5F6FA)),
        onBackground = Color(0xFF1A1C22),
        surface = seedLight.copy(alpha = 0.03f).compositeOn(Color(0xFFFDFDFE)),
        onSurface = Color(0xFF1A1C22),
        surfaceContainer = seedLight.copy(alpha = 0.08f).compositeOn(Color(0xFFF6F7FB)),
        surfaceContainerHigh = seedLight.copy(alpha = 0.12f).compositeOn(Color(0xFFF4F5F9)),
        surfaceContainerHighest = seedLight.copy(alpha = 0.16f).compositeOn(Color(0xFFF2F3F8)),
        surfaceVariant = seedLight.copy(alpha = 0.15f).compositeOn(Color(0xFFE8EAF2)),
    )
}

private fun darkSchemeFor(seedDark: Color): androidx.compose.material3.ColorScheme {
    return darkColorScheme(
        primary = seedDark,
        onPrimary = Color(0xFF10141C),
        primaryContainer = seedDark.copy(alpha = 0.28f).compositeOn(Color(0xFF161A22)),
        onPrimaryContainer = Color(0xFFF2F3F7),
        secondary = seedDark,
        secondaryContainer = seedDark.copy(alpha = 0.24f).compositeOn(Color(0xFF161A22)),
        onSecondaryContainer = Color(0xFFF2F3F7),
        tertiary = seedDark,
        error = Color(0xFFFFB4AB),
        // 夜间三级分层同样由种子色微染，背景最深、卡面/容器逐级提亮，边界清晰且色调统一
        background = seedDark.copy(alpha = 0.05f).compositeOn(Color(0xFF090B10)),
        onBackground = Color(0xFFE3E6EE),
        surface = seedDark.copy(alpha = 0.07f).compositeOn(Color(0xFF12151C)),
        onSurface = Color(0xFFE3E6EE),
        surfaceContainer = seedDark.copy(alpha = 0.09f).compositeOn(Color(0xFF161A22)),
        surfaceContainerHigh = seedDark.copy(alpha = 0.13f).compositeOn(Color(0xFF1A1F28)),
        surfaceContainerHighest = seedDark.copy(alpha = 0.17f).compositeOn(Color(0xFF1E242E)),
        surfaceVariant = seedDark.copy(alpha = 0.25f).compositeOn(Color(0xFF222834)),
    )
}

/** 简易混色：seed(alpha) 叠在底色上（用于容器色） */
private fun Color.compositeOn(base: Color): Color {
    val a = alpha
    return Color(
        red = red * a + base.red * (1 - a),
        green = green * a + base.green * (1 - a),
        blue = blue * a + base.blue * (1 - a),
        alpha = 1f,
    )
}

/**
 * MD3 主题：
 * - themeMode: 0跟随系统 / 1日间 / 2夜间
 * - themeColor: "dynamic"(Android 12+ 随壁纸取色) 或内置色 key（blue/teal/violet/amber/rose）
 */
@Composable
fun KgCheckinTheme(
    themeMode: Int = 0,
    themeColor: String = "dynamic",
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val option = THEME_OPTIONS.firstOrNull { it.key == themeColor } ?: THEME_OPTIONS.first()
    val colorScheme = when {
        option.key == "dynamic" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkSchemeFor(option.darkColor)
        else -> lightSchemeFor(option.lightColor)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
