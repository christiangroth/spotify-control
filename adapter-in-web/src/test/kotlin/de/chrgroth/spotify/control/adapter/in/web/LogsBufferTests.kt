package de.chrgroth.spotify.control.adapter.`in`.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.logging.Level
import java.util.logging.LogRecord

class LogsBufferTests {

  @Test
  fun `keeps newest entries first and only warn error levels`() {
    val buffer = LogsBuffer(nowMillisProvider = { 10_000L }, retentionMillis = 100_000L, maxEntries = 10)
    buffer.append(createRecord(level = Level.INFO, message = "info", millis = 9_000L))
    buffer.append(createRecord(level = Level.WARNING, message = "warn", millis = 9_100L))
    buffer.append(createRecord(level = Level.SEVERE, message = "error", millis = 9_200L))

    val recent = buffer.getRecent()

    assertThat(recent).hasSize(2)
    assertThat(recent.map { it.level }).containsExactly("ERROR", "WARN")
    assertThat(recent.map { it.message }).containsExactly("error", "warn")
  }

  @Test
  fun `drops entries older than retention window`() {
    var now = 20_000L
    val buffer = LogsBuffer(nowMillisProvider = { now }, retentionMillis = 1_000L, maxEntries = 10)
    buffer.append(createRecord(level = Level.WARNING, message = "old", millis = 18_999L))
    buffer.append(createRecord(level = Level.WARNING, message = "new", millis = 19_500L))

    assertThat(buffer.getRecent().map { it.message }).containsExactly("new")

    now = 21_000L
    assertThat(buffer.getRecent()).isEmpty()
  }

  @Test
  fun `respects max entries and simplifies internal class names`() {
    val buffer = LogsBuffer(nowMillisProvider = { 10_000L }, retentionMillis = 100_000L, maxEntries = 2)
    buffer.append(createRecord(level = Level.WARNING, message = "first", millis = 1_000L, loggerName = "de.chrgroth.spotify.control.FooService"))
    buffer.append(createRecord(level = Level.WARNING, message = "second", millis = 2_000L, loggerName = "de.chrgroth.spotify.control.BarService"))
    buffer.append(createRecord(level = Level.WARNING, message = "third", millis = 3_000L, loggerName = "org.example.Remote"))

    val recent = buffer.getRecent()
    assertThat(recent).hasSize(2)
    assertThat(recent.map { it.message }).containsExactly("third", "second")
    assertThat(recent[1].className).isEqualTo("BarService")
    assertThat(recent[0].className).isEqualTo("org.example.Remote")
  }

  private fun createRecord(
    level: Level,
    message: String,
    millis: Long,
    loggerName: String = "de.chrgroth.spotify.control.TestClass",
  ): LogRecord =
    LogRecord(level, message).apply {
      this.loggerName = loggerName
      this.millis = millis
    }
}
