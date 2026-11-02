package com.kgcheckin.app.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 签到日志事件总线（进程级单例）。
 *
 * 定时服务（CheckinService）执行签到时，通过它把日志实时广播给
 * 处于存续状态的签到页 ViewModel —— 用户打开 APP 即可看到定时执行的完整过程，
 * 与手动点击「开始签到」的日志展示体验完全一致。
 */
object CheckinLogBus {

    private val _events = MutableSharedFlow<LogEvent>(extraBufferCapacity = 4096)

    val events: SharedFlow<LogEvent> = _events.asSharedFlow()

    fun post(event: LogEvent) {
        _events.tryEmit(event)
    }
}
