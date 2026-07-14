package de.chrgroth.spotify.control.adapter.`in`.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HttpResponseMetricsTests {

  private val metrics = HttpResponseMetrics(slowResponseThresholdMs = 100L)

  @Test
  fun `timed returns block result`() {
    val result = metrics.timed("test.op") { "actual" }
    assertThat(result).isEqualTo("actual")
  }

  @Test
  fun `detail returns block result`() {
    val result = metrics.timed("test.op") { details ->
      details.detail("test.op.step") { "actual" }
    }
    assertThat(result).isEqualTo("actual")
  }
}
