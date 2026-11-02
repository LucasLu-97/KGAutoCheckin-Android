package com.kgcheckin.app.core

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 签到日志持久化（按天存文件，保留30天）：
 * 重启 APP 后当天历次执行（手动 / 定时 / 打开即签）的日志全部可回看。
 *
 * 文件：files/checkin_logs/yyyy-MM-dd.log，每行 `HH:mm:ss|LEVEL|message`
 */
class LogArchive(context: Context) {

    private val dir = File(context.filesDir, "checkin_logs").apply { mkdirs() }
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val dayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private fun fileFor(date: LocalDate): File = File(dir, date.format(dayFormat) + ".log")

    /** 追加一条日志（仅当天实时写入） */
    fun append(event: LogEvent) {
        runCatching {
            fileFor(LocalDate.now()).appendText(
                "${event.time.format(timeFormat)}|${event.level.name}|${event.message.replace("\n", " ⏎ ")}\n"
            )
        }
    }

    /** 读取某天全部日志（重启后恢复用） */
    fun loadDay(date: LocalDate): List<LogEvent> {
        val file = fileFor(date)
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines().mapNotNull { line ->
                val parts = line.split("|", limit = 3)
                if (parts.size < 3) return@mapNotNull null
                LogEvent(
                    time = runCatching { LocalTime.parse(parts[0]) }.getOrElse { LocalTime.now() },
                    level = runCatching { LogLevel.valueOf(parts[1]) }.getOrDefault(LogLevel.INFO),
                    message = parts[2],
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 清空某天日志文件（“清除日志”按钮用） */
    fun clearDay(date: LocalDate = LocalDate.now()) {
        runCatching { fileFor(date).delete() }
    }

    /** 清空全部日志存档（重置应用数据用） */
    fun clearAll() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    /** 清理30天前的旧日志文件 */
    fun cleanup() {
        runCatching {
            val cutoff = LocalDate.now().minusDays(MAX_KEEP_DAYS).toEpochDay()
            dir.listFiles()?.forEach { f ->
                val date = runCatching {
                    LocalDate.parse(f.nameWithoutExtension, dayFormat)
                }.getOrNull() ?: return@forEach
                if (date.toEpochDay() < cutoff) f.delete()
            }
        }
    }

    /** 调试辅助：当天文件是否存在 */
    fun existsToday(): Boolean = fileFor(LocalDate.now()).exists()

    companion object {
        private const val MAX_KEEP_DAYS = 30L
    }
}
