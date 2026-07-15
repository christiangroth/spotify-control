package de.chrgroth.spotify.control.adapter.`in`.http.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.quarkus.runtime.StartupEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApplicationInfoMetricsTests {

  private val meterRegistry = SimpleMeterRegistry()
  private val metrics = ApplicationInfoMetrics(meterRegistry).apply { version = "1.2.3" }

  @Test
  fun `gauge exposes the running application version as a tag`() {
    metrics.onStartup(StartupEvent())

    val gauge = meterRegistry.find("application.info").tag("version", "1.2.3").gauge()

    assertThat(gauge?.value()).isEqualTo(1.0)
  }
}
