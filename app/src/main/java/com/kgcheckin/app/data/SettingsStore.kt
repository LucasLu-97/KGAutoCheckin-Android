package com.kgcheckin.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kg_settings")

/** 自动化与个性化设置 */
data class CheckinSettings(
    /** 方案A：每日定时自动签到（前台服务后台执行），每天在时间窗口内随机抽一个时刻 */
    val autoEnabled: Boolean = false,
    /** 签到时间窗口（同一天内，结束需晚于开始；不跨午夜） */
    val windowStartHour: Int = 9,
    val windowStartMinute: Int = 0,
    val windowEndHour: Int = 10,
    val windowEndMinute: Int = 0,
    /** 已抽定的下一次执行时刻（epoch 毫秒；0=尚未抽定）。重启/打开APP按此值原样恢复闹钟，不重新抽 */
    val nextPickEpochMillis: Long = 0L,
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
        // 旧版单一执行时刻的键：保留只读，作为窗口起点的历史数据迁移来源（新代码不再写入）
        val LEGACY_HOUR = intPreferencesKey("auto_checkin_hour")
        val LEGACY_MINUTE = intPreferencesKey("auto_checkin_minute")
        val WINDOW_START_HOUR = intPreferencesKey("window_start_hour")
        val WINDOW_START_MINUTE = intPreferencesKey("window_start_minute")
        val WINDOW_END_HOUR = intPreferencesKey("window_end_hour")
        val WINDOW_END_MINUTE = intPreferencesKey("window_end_minute")
        val NEXT_PICK = longPreferencesKey("next_pick_epoch_millis")
        val OPEN = booleanPreferencesKey("open_to_checkin")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val THEME_COLOR = stringPreferencesKey("theme_color")
    }

    val settings: Flow<CheckinSettings> = context.dataStore.data.map { p ->
        // 老版本迁移：原单一执行时刻直接作为窗口起点，窗口默认向后拉长 1 小时（23:59 封顶），
        // 老用户升级后无需重新设置
        val legacyHour = (p[Keys.LEGACY_HOUR] ?: 9).coerceIn(0, 23)
        val legacyMinute = (p[Keys.LEGACY_MINUTE] ?: 0).coerceIn(0, 59)
        val legacyEnd = minOf(legacyHour * 60 + legacyMinute + 60, 23 * 60 + 59)
        CheckinSettings(
            autoEnabled = p[Keys.AUTO] ?: false,
            windowStartHour = (p[Keys.WINDOW_START_HOUR] ?: legacyHour).coerceIn(0, 23),
            windowStartMinute = (p[Keys.WINDOW_START_MINUTE] ?: legacyMinute).coerceIn(0, 59),
            windowEndHour = (p[Keys.WINDOW_END_HOUR] ?: legacyEnd / 60).coerceIn(0, 23),
            windowEndMinute = (p[Keys.WINDOW_END_MINUTE] ?: legacyEnd % 60).coerceIn(0, 59),
            nextPickEpochMillis = p[Keys.NEXT_PICK] ?: 0L,
            openToCheckin = p[Keys.OPEN] ?: false,
            themeMode = (p[Keys.THEME_MODE] ?: 0).coerceIn(0, 2),
            themeColor = p[Keys.THEME_COLOR] ?: "dynamic",
        )
    }

    suspend fun setAutoEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO] = enabled }
    }

    /** 设置签到时间窗口（界面保证结束晚于开始；存储层不强制，调度层对非法窗口有兜底） */
    suspend fun setWindow(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        context.dataStore.edit {
            it[Keys.WINDOW_START_HOUR] = startHour.coerceIn(0, 23)
            it[Keys.WINDOW_START_MINUTE] = startMinute.coerceIn(0, 59)
            it[Keys.WINDOW_END_HOUR] = endHour.coerceIn(0, 23)
            it[Keys.WINDOW_END_MINUTE] = endMinute.coerceIn(0, 59)
        }
    }

    /** 记录已抽定的下一次执行时刻（由调度层写入；0 表示作废待重抽） */
    suspend fun setNextPick(epochMillis: Long) {
        context.dataStore.edit { it[Keys.NEXT_PICK] = epochMillis }
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
