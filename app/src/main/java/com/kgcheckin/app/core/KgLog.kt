package com.kgcheckin.app.core

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 日志级别（对应脚本 colorOut 的彩色输出，参考截图1配色）：
 * - INFO：普通信息（深灰）
 * - ACCOUNT：账号标识（紫色）
 * - STEP：操作步骤（深棕）
 * - SUCCESS：成功（绿色）
 * - DATETIME：日期与 VIP 到期信息（蓝色）
 * - WARN：警告（琥珀）
 * - ERROR：失败（红）
 */
enum class LogLevel { INFO, ACCOUNT, STEP, SUCCESS, DATETIME, WARN, ERROR }

/** 一条执行过程日志 */
data class LogEvent(
    val time: LocalTime = LocalTime.now(),
    val level: LogLevel = LogLevel.INFO,
    val message: String,
) {
    val timeText: String get() = time.format(TIME_FORMAT)

    companion object {
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")

        fun info(msg: String) = LogEvent(level = LogLevel.INFO, message = msg)
        fun account(msg: String) = LogEvent(level = LogLevel.ACCOUNT, message = msg)
        fun step(msg: String) = LogEvent(level = LogLevel.STEP, message = msg)
        fun success(msg: String) = LogEvent(level = LogLevel.SUCCESS, message = msg)
        fun datetime(msg: String) = LogEvent(level = LogLevel.DATETIME, message = msg)
        fun warn(msg: String) = LogEvent(level = LogLevel.WARN, message = msg)
        fun error(msg: String) = LogEvent(level = LogLevel.ERROR, message = msg)
    }
}
