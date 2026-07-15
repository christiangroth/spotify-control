package de.chrgroth.spotify.control.adapter.`in`.http.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HttpResponseMetricsTests {

  private val meterRegistry = SimpleMeterRegistry()
  private val metrics = HttpResponseMetrics(
    meterRegistry = meterRegistry,
    slowResponseThresholdMs = 100L,
  )

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

  @Test
  fun `timed records response duration timer`() {
    metrics.timed("test.timer") { "actual" }

    val timer = meterRegistry.find("http.response").tag("operation", "test.timer").timer()
    assertThat(timer).isNotNull()
    assertThat(timer!!.count()).isEqualTo(1L)
  }

  @Test
  fun `timed records slow response counter when threshold exceeded`() {
    metrics.timed("test.slow") {
      Thread.sleep(150L)
      "actual"
    }

    val counter = meterRegistry.find("http.response.slow").tag("operation", "test.slow").counter()
    assertThat(counter).isNotNull()
    assertThat(counter!!.count()).isEqualTo(1.0)
  }
}
