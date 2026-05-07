package de.chrgroth.spotify.control.adapter.`in`.web

import java.util.logging.Level
import java.util.logging.LogRecord

class LogsBuffer(
  private val nowMillisProvider: () -> Long = { System.currentTimeMillis() },
  private val retentionMillis: Long = RETENTION_MILLIS,
  private val maxEntries: Int = MAX_ENTRIES,
) {

  private val entries = ArrayDeque<LogUiEntry>()

  @Synchronized
  fun append(record: LogRecord) {
    if (record.level.intValue() < Level.WARNING.intValue()) return

    val nowMillis = nowMillisProvider()
    cleanup(nowMillis)
    if (record.millis < nowMillis - retentionMillis) return

    entries.addLast(
      LogUiEntry(
        timestampEpochMillis = record.millis,
        level = if (record.level.intValue() >= Level.SEVERE.intValue()) "ERROR" else "WARN",
        className = simplifyClassName(record.loggerName),
        message = record.message.orEmpty(),
        stacktrace = record.thrown?.stackTraceToString()?.takeIf { it.isNotBlank() },
      ),
    )

    while (entries.size > maxEntries) {
      entries.removeFirst()
    }
  }

  @Synchronized
  fun getRecent(): List<LogUiEntry> {
    cleanup(nowMillisProvider())
    return entries.asReversed()
  }

  private fun cleanup(nowMillis: Long) {
    val cutoffMillis = nowMillis - retentionMillis
    while (entries.isNotEmpty() && entries.first().timestampEpochMillis < cutoffMillis) {
      entries.removeFirst()
    }
  }

  companion object {
    const val MAX_ENTRIES = 500
    const val RETENTION_MILLIS = 2 * 60 * 60 * 1000L

    fun simplifyClassName(loggerName: String?): String {
      if (loggerName.isNullOrBlank()) return "-"
      if (!loggerName.startsWith("de.chrgroth.")) return loggerName
      return loggerName.substringAfterLast('.')
    }
  }
}

data class LogUiEntry(
  val timestampEpochMillis: Long,
  val level: String,
  val className: String,
  val message: String,
  val stacktrace: String?,
)
