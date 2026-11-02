package com.kgcheckin.app.ui.account

import android.graphics.Bitmap
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kgcheckin.app.KgApplication
import com.kgcheckin.app.data.Account
import com.kgcheckin.app.data.KgRepository
import com.kgcheckin.app.data.QrLoginState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 账户登录页 ViewModel：账号列表管理 + 扫码/验证码两种登录方式 */
class AccountViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = getApplication<KgApplication>().container.repository

    val accounts: StateFlow<List<Account>> = repo.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ══ 添加账号底部弹层 ══
    private val _sheetVisible = MutableStateFlow(false)
    val sheetVisible: StateFlow<Boolean> = _sheetVisible.asStateFlow()

    /** 登录方式：true=扫码，false=验证码 */
    private val _qrMode = MutableStateFlow(true)
    val qrMode: StateFlow<Boolean> = _qrMode.asStateFlow()

    private val _qrState = MutableStateFlow<QrLoginState>(QrLoginState.Idle)
    val qrState: StateFlow<QrLoginState> = _qrState.asStateFlow()

    /** 短信登录状态提示 */
    private val _smsMessage = MutableStateFlow("")
    val smsMessage: StateFlow<String> = _smsMessage.asStateFlow()

    /** 验证码发送倒计时（秒，0 表示可发送） */
    private val _smsCountdown = MutableStateFlow(0)
    val smsCountdown: StateFlow<Int> = _smsCountdown.asStateFlow()

    private var qrPollJob: Job? = null
    private var countdownJob: Job? = null

    fun openSheet() {
        _sheetVisible.value = true
    }

    fun closeSheet() {
        _sheetVisible.value = false
        stopQrPoll()
    }

    fun switchMode(qr: Boolean) {
        _qrMode.value = qr
        stopQrPoll()
        _qrState.value = QrLoginState.Idle
        _smsMessage.value = ""
    }

    // ════════════════ 扫码登录 ════════════════

    /** 生成新二维码并开始轮询（2 分钟有效期，前 10 次 3s、其后 5s，等价脚本节奏） */
    fun startQrLogin() {
        stopQrPoll()
        _qrState.value = QrLoginState.Loading
        viewModelScope.launch {
            try {
                val (content, bitmap) = repo.createQrSession()
                _qrState.value = QrLoginState.Ready(content, bitmap, System.currentTimeMillis())
                startQrPoll(content)
            } catch (e: Exception) {
                _qrState.value = QrLoginState.Error("二维码生成失败：${e.message}")
            }
        }
    }

    private fun startQrPoll(qrContent: String) {
        qrPollJob = viewModelScope.launch {
            val startAt = System.currentTimeMillis()
            var polls = 0
            while (isActive) {
                // 二维码有效期约 2 分钟
                if (System.currentTimeMillis() - startAt > 120_000) {
                    _qrState.value = QrLoginState.Expired
                    break
                }
                delay(if (polls < 10) 3000L else 5000L)
                polls++
                val status = runCatching { repo.pollQrOnce(qrContent) }.getOrNull() ?: continue
                when (status) {
                    0 -> { _qrState.value = QrLoginState.Expired; break }
                    2 -> _qrState.value = QrLoginState.Scanned
                    4 -> {
                        // 登录成功：取 token/userid 完成初始化
                        try {
                            val account = finishQrLogin(qrContent)
                            _qrState.value = QrLoginState.Success(account)
                        } catch (e: Exception) {
                            _qrState.value = QrLoginState.Error("登录成功但初始化失败：${e.message}")
                        }
                        return@launch
                    }
                }
            }
        }
    }

    /** 从轮询接口再一次拿到 token/userid 并完成账号落库 */
    private suspend fun finishQrLogin(qrContent: String): Account {
        // pollQrOnce 已确认 status=4，这里直接再调一次接口解析 token
        val qrcode = qrContent.removePrefix(com.kgcheckin.app.core.KgConfig.QR_LOGIN_URL_PREFIX)
        // 通过 repository 暴露的内部通道拿凭证
        return repo.loginByQrToken(qrcode)
    }

    private fun stopQrPoll() {
        qrPollJob?.cancel()
        qrPollJob = null
    }

    // ════════════════ 验证码登录 ════════════════

    fun sendSmsCode(mobile: String) {
        if (_smsCountdown.value > 0) return
        if (!Regex("^1\\d{10}$").matches(mobile)) {
            _smsMessage.value = "请输入正确的 11 位手机号"
            return
        }
        _smsMessage.value = ""
        viewModelScope.launch {
            try {
                repo.sendSmsCode(mobile)
                _smsMessage.value = "验证码已发送，请注意查收短信"
                startCountdown(60)
            } catch (e: Exception) {
                _smsMessage.value = "发送失败：${e.message}"
            }
        }
    }

    fun loginBySms(mobile: String, code: String) {
        if (mobile.isBlank() || code.isBlank()) {
            _smsMessage.value = "请输入手机号和验证码"
            return
        }
        _smsMessage.value = "正在登录 ..."
        viewModelScope.launch {
            try {
                val account = repo.loginBySms(mobile, code)
                _smsMessage.value = "登录成功：${account.displayNickname()}"
                stopQrPoll()
                _qrState.value = QrLoginState.Success(account)
            } catch (e: Exception) {
                _smsMessage.value = "登录失败：${e.message}"
            }
        }
    }

    private fun startCountdown(seconds: Int) {
        countdownJob?.cancel()
        _smsCountdown.value = seconds
        countdownJob = viewModelScope.launch {
            while (_smsCountdown.value > 0) {
                delay(1000)
                _smsCountdown.value = _smsCountdown.value - 1
            }
        }
    }

    // ════════════════ 账号管理 ════════════════

    fun deleteAccount(userid: String) {
        viewModelScope.launch {
            repo.deleteAccount(userid)
        }
    }

    override fun onCleared() {
        stopQrPoll()
        super.onCleared()
    }
}
