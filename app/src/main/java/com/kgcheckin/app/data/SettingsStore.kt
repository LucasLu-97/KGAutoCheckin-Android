package com.kgcheckin.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kg_settings")

/** 自动化与个性化设置 */
data class CheckinSettings(
    /** 方案A：每日定时自动签到（前台服务后台执行） */
    val autoEnabled: Boolean = false,
    val hour: Int = 9,
    val minute: Int = 0,
    /** 方案B：打开APP即自动签到（配合手机自动任务拉起APP） */
    val openToCheckin: Boolean = false,
    /** 深浅模式：0跟随系统 / 1日间 / 2夜间 */
    val themeMode: Int = 0,
    /** 主题色：dynamic(壁纸取色) / blue / teal / violet / amber / rose */
    val themeColor: String = "dynamic",
)

/** 设置持久化（DataStore）；文件名 kg_settings.preferences_pb */
class SettingsStore(private val context: Context) {

    private object Keys {
        val AUTO = booleanPreferencesKey("auto_checkin_enabled")
        val HOUR = intPreferencesKey("auto_checkin_hour")
        val MINUTE = intPreferencesKey("auto_checkin_minute")
        val OPEN = booleanPreferencesKey("open_to_checkin")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val THEME_COLOR = androidx.datastore.preferences.core.stringPreferencesKey("theme_color")
    }

    val settings: Flow<CheckinSettings> = context.dataStore.data.map { p ->
        CheckinSettings(
            autoEnabled = p[Keys.AUTO] ?: false,
            hour = (p[Keys.HOUR] ?: 9).coerceIn(0, 23),
            minute = (p[Keys.MINUTE] ?: 0).coerceIn(0, 59),
            openToCheckin = p[Keys.OPEN] ?: false,
            themeMode = (p[Keys.THEME_MODE] ?: 0).coerceIn(0, 2),
            themeColor = p[Keys.THEME_COLOR] ?: "dynamic",
        )
    }

    suspend fun setAutoEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO] = enabled }
    }

    suspend fun setTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[Keys.HOUR] = hour.coerceIn(0, 23)
            it[Keys.MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setOpenToCheckin(enabled: Boolean) {
        context.dataStore.edit { it[Keys.OPEN] = enabled }
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.coerceIn(0, 2) }
    }

    suspend fun setThemeColor(color: String) {
        context.dataStore.edit { it[Keys.THEME_COLOR] = color }
    }

    /** 清空全部设置（重置应用数据用；账号凭据不在本存储） */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
