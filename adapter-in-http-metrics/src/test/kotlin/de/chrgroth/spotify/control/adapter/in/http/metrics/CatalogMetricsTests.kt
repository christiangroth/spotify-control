package de.chrgroth.spotify.control.adapter.`in`.http.metrics

import de.chrgroth.spotify.control.domain.model.catalog.CatalogStats
import de.chrgroth.spotify.control.domain.port.out.catalog.CatalogStatsPort
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.quarkus.runtime.StartupEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CatalogMetricsTests {

  private val catalogStatsPort: CatalogStatsPort = mockk()
  private val meterRegistry = SimpleMeterRegistry()
  private val metrics = CatalogMetrics(catalogStatsPort, meterRegistry)

  @Test
  fun `gauges expose catalog stats counts`() {
    every { catalogStatsPort.current() } returns CatalogStats(artistCount = 3L, albumCount = 5L, trackCount = 42L)

    metrics.onStartup(StartupEvent())

    assertThat(meterRegistry.find("app.catalog.artists").gauge()?.value()).isEqualTo(3.0)
    assertThat(meterRegistry.find("app.catalog.tracks").gauge()?.value()).isEqualTo(42.0)
    assertThat(meterRegistry.find("app.catalog.albums").gauge()?.value()).isEqualTo(5.0)
  }

  @Test
  fun `all three gauges share a single port read per scrape`() {
    every { catalogStatsPort.current() } returns CatalogStats(artistCount = 3L, albumCount = 5L, trackCount = 42L)

    metrics.onStartup(StartupEvent())
    meterRegistry.find("app.catalog.artists").gauge()?.value()
    meterRegistry.find("app.catalog.tracks").gauge()?.value()
    meterRegistry.find("app.catalog.albums").gauge()?.value()

    verify(exactly = 1) { catalogStatsPort.current() }
  }
}
