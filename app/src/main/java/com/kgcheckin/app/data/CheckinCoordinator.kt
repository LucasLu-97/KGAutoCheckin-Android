package com.kgcheckin.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 签到执行全局协调器（进程级单点锁）。
 *
 * 防止三种触发路径并发重复执行（重复领取虽幂等但浪费 4~5 分钟且触发风控风险）：
 * - 手动点击「开始签到」
 * - 打开APP自动签到（方案B）
 * - 每日定时服务（方案A）
 */
object CheckinCoordinator {

    private val _running = MutableStateFlow(false)

    /** 是否有任何签到执行正在进行（UI 据此提示） */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** 本次执行开始时间戳（ms），未运行时为 0 */
    @Volatile var startedAt: Long = 0
        private set

    /** 最近一次执行耗时（ms），供完成后展示固定耗时文本 */
    @Volatile var lastDurationMs: Long = 0
        private set

    /** 手动清除日志等场景同步重置耗时展示 */
    fun resetLastDuration() {
        lastDurationMs = 0
    }

    /** 尝试占用执行锁；已在执行则返回 false */
    fun tryAcquire(): Boolean {
        synchronized(this) {
            if (_running.value) return false
            _running.value = true
            startedAt = System.currentTimeMillis()
            return true
        }
    }

    fun release() {
        synchronized(this) {
            if (startedAt > 0) {
                lastDurationMs = System.currentTimeMillis() - startedAt
                startedAt = 0
            }
            _running.value = false
        }
    }
}
