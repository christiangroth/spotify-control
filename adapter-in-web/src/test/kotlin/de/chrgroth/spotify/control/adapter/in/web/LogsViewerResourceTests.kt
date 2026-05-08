package de.chrgroth.spotify.control.adapter.`in`.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LogsViewerResourceTests {

  @Test
  fun `groups logs by class and level and sorts by level then group size`() {
    val resource = LogsViewerResource()
    val groups = resource.groupLogsByClassAndType(
      listOf(
        createEntry(timestamp = 1_000L, level = "WARN", className = "ClassB", message = "warn-b-old"),
        createEntry(timestamp = 2_000L, level = "WARN", className = "ClassB", message = "warn-b-new"),
        createEntry(timestamp = 3_000L, level = "ERROR", className = "ClassA", message = "error-a"),
        createEntry(timestamp = 4_000L, level = "ERROR", className = "ClassC", message = "error-c-old"),
        createEntry(timestamp = 5_000L, level = "ERROR", className = "ClassC", message = "error-c-new"),
      ),
    )

    assertThat(groups.map { "${it.className}:${it.level}:${it.count}" })
      .containsExactly(
        "ClassC:ERROR:2",
        "ClassA:ERROR:1",
        "ClassB:WARN:2",
      )
    assertThat(groups.first().entries.map { it.message }).containsExactly("error-c-new", "error-c-old")
  }

  private fun createEntry(
    timestamp: Long,
    level: String,
    className: String,
    message: String,
  ) = LogUiEntry(
    timestampEpochMillis = timestamp,
    time = "00:00:00",
    level = level,
    className = className,
    message = message,
    stacktrace = null,
  )
}
