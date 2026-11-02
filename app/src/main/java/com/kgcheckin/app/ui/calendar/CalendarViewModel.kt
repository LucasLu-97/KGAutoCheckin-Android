package com.kgcheckin.app.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kgcheckin.app.KgApplication
import com.kgcheckin.app.data.Account
import com.kgcheckin.app.data.CheckinRecord
import com.kgcheckin.app.ui.components.AvatarCache
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** 签到日历页 ViewModel：会员到期状态 + 按月查看领取记录，支持切换账号/月份 */
class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    private val container = getApplication<KgApplication>().container
    private val repo = container.repository

    val accounts: StateFlow<List<Account>> = repo.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedUserid = MutableStateFlow<String?>(null)
    val selectedUserid: StateFlow<String?> = _selectedUserid.asStateFlow()

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    private val _firstUseDate = MutableStateFlow<LocalDate?>(null)
    val firstUseDate: StateFlow<LocalDate?> = _firstUseDate.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** 下拉刷新结果提示（同步服务器历史记录条数等） */
    private val _refreshMessage = MutableStateFlow<String?>(null)
    val refreshMessage: StateFlow<String?> = _refreshMessage.asStateFlow()

    /** 当月签到记录（key = epochDay） */
    @OptIn(ExperimentalCoroutinesApi::class)
    val monthRecords: StateFlow<Map<Long, CheckinRecord>> =
        combine(_selectedUserid, _month) { uid, m -> uid to m }
            .flatMapLatest { (uid, m) ->
                if (uid == null) {
                    flowOf(emptyMap())
                } else {
                    container.recordDao.queryMonth(
                        uid, m.atDay(1).toEpochDay(), m.atEndOfMonth().toEpochDay()
                    ).map { list -> list.associateBy { it.epochDay } }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun prevMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
    }

    fun goToday() {
        _month.value = YearMonth.now()
    }

    fun selectAccount(userid: String) {
        _selectedUserid.value = userid
        refreshFirstUseDate(userid)
    }

    /** 默认选中第一个账号（仅在未选择时） */
    fun ensureSelection() {
        if (_selectedUserid.value == null) {
            accounts.value.firstOrNull()?.userid?.let {
                _selectedUserid.value = it
                refreshFirstUseDate(it)
            }
        }
    }

    private fun refreshFirstUseDate(userid: String) {
        viewModelScope.launch {
            _firstUseDate.value = container.recordDao.firstRecord(userid)
                ?.let { LocalDate.ofEpochDay(it.epochDay) }
        }
    }

    /** 下拉刷新：按当前查看月份同步服务器签到领取历史 + 拉取最新会员信息 */
    fun refresh() {
        val account = accounts.value.firstOrNull { it.userid == _selectedUserid.value }
            ?: accounts.value.firstOrNull()
        if (account == null || _refreshing.value) return
        val viewMonth = _month.value
        val isCurrentMonth = viewMonth == java.time.YearMonth.now()
        _refreshing.value = true
        viewModelScope.launch {
            try {
                AvatarCache.bump() // 头像绕过缓存重新拉取
                repo.refreshAccountInfo(account)
                val targetMonth = if (isCurrentMonth) null else viewMonth
                val synced = repo.syncServerRecords(account, targetMonth)
                _refreshMessage.value = when {
                    isCurrentMonth && synced > 0 -> "已同步 $synced 条记录"
                    isCurrentMonth -> "已刷新，无新增记录"
                    synced > 0 -> "已同步 ${viewMonth.year}年${viewMonth.monthValue}月 $synced 条"
                    else -> "${viewMonth.year}年${viewMonth.monthValue}月暂不支持查询，随使用积累"
                }
                _selectedUserid.value?.let { refreshFirstUseDate(it) }
            } catch (e: Exception) {
                _refreshMessage.value = "刷新失败：${e.message ?: "网络异常"}"
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun today(): LocalDate = LocalDate.now()
}
