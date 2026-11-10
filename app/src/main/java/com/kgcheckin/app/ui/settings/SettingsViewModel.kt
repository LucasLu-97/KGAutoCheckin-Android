package com.kgcheckin.app.ui.settings

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kgcheckin.app.KgApplication
import com.kgcheckin.app.data.CheckinCoordinator
import com.kgcheckin.app.data.CheckinSettings
import com.kgcheckin.app.schedule.CheckinScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** 应用内更新检查状态 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val current: String) : UpdateState
    data class Available(
        val current: String,
        val latest: String,
        /** 与设备 ABI 匹配的 APK 下载地址（无匹配则回退 Release 页） */
        val downloadUrl: String,
        val releaseUrl: String,
    ) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** 更多设置页 ViewModel：定时设置读写、闹钟调度、GitHub 版本检查 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = getApplication<KgApplication>().container

    /** 版本检查走 api.github.com（国内直连可达，无需代理） */
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    val settings: StateFlow<CheckinSettings> = container.settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CheckinSettings())

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    /** 检查 GitHub 最新 Release（api.github.com 直连） */
    fun checkUpdate(currentVersion: String) {
        if (_updateState.value == UpdateState.Checking) return
        _updateState.value = UpdateState.Checking
        viewModelScope.launch {
            _updateState.value = withContext(Dispatchers.IO) {
                runCatching {
                    val request = okhttp3.Request.Builder()
                        .url("https://api.github.com/repos/LucasLu-97/KGAutoCheckin-Android/releases/latest")
                        .header("User-Agent", "KGAutoCheckin-Android")
                        .build()
                    okHttp.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) throw IllegalStateException("GitHub 响应异常 HTTP ${resp.code}")
                        val body = resp.body?.string() ?: throw IllegalStateException("空响应")
                        val root = json.parseToJsonElement(body).jsonObject
                        val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull
                            ?: throw IllegalStateException("响应缺少 tag_name")
                        val latest = tag.removePrefix("v")
                        val releaseUrl = root["html_url"]?.jsonPrimitive?.contentOrNull ?: ""
                        val assets = root["assets"]?.jsonArray ?: JsonArray(emptyList())

                        // 优先匹配设备主 ABI 的安装包，否则取第一个附件，最后回退 Release 页
                        val preferredAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
                        val assetList = assets.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
                        val matched = assetList.firstOrNull {
                            it["name"]?.jsonPrimitive?.contentOrNull?.contains(preferredAbi) == true
                        } ?: assetList.firstOrNull()
                        val downloadUrl = matched
                            ?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
                            ?: releaseUrl

                        if (isNewer(latest, currentVersion)) {
                            UpdateState.Available(currentVersion, latest, downloadUrl, releaseUrl)
                        } else {
                            UpdateState.UpToDate(currentVersion)
                        }
                    }
                }.getOrElse { UpdateState.Failed(it.message ?: "网络异常") }
            }
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    /** 语义化版本比较（数字段逐位比较，避免 1.3.10 < 1.3.5 的字典序误判） */
    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split('.').map { it.toIntOrNull() ?: 0 }
        val c = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrNull(i) ?: 0
            val b = c.getOrNull(i) ?: 0
            if (a != b) return a > b
        }
        return false
    }

    /** 开关每日定时；开启即在窗口内抽定下一个随机时刻并排布闹钟 */
    fun setAutoEnabled(enabled: Boolean) {
        val context = getApplication<KgApplication>()
        viewModelScope.launch {
            container.settingsStore.setAutoEnabled(enabled)
            if (enabled) {
                CheckinScheduler.ensureArmed(context, allowToday = true)
            } else {
                CheckinScheduler.cancel(context)
            }
        }
    }

    /** 修改签到时间窗口；已开启则作废旧抽定时刻、按新窗口重抽 */
    fun setAutoWindow(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        val context = getApplication<KgApplication>()
        viewModelScope.launch {
            container.settingsStore.setWindow(startHour, startMinute, endHour, endMinute)
            // 无论开关状态都作废旧抽定时刻，避免旧窗口的残余时刻被误用
            container.settingsStore.setNextPick(0L)
            val s = container.settingsStore.settings.first()
            if (s.autoEnabled) {
                CheckinScheduler.ensureArmed(context, allowToday = true)
            }
        }
    }

    /** 开关打开即签 */
    fun setOpenToCheckin(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsStore.setOpenToCheckin(enabled)
        }
    }

    /** 深浅模式：0跟随系统 / 1日间 / 2夜间 */
    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            container.settingsStore.setThemeMode(mode)
        }
    }

    /** 主题色 */
    fun setThemeColor(color: String) {
        viewModelScope.launch {
            container.settingsStore.setThemeColor(color)
        }
    }

    /** 重置应用数据：清空签到历史/日志/设置，保留账号登录 */
    fun resetAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            container.clearCheckinHistory()
            container.logArchive.clearAll()
            container.settingsStore.clearAll()
            CheckinCoordinator.resetLastDuration()
            // 通知签到页清空内存中已展示的日志（否则文件已删、界面仍显示旧内容）
            com.kgcheckin.app.core.CheckinLogBus.requestClear()
            onDone()
        }
    }
}
