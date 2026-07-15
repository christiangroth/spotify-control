package de.chrgroth.spotify.control.adapter.`in`.http.metrics

import de.chrgroth.spotify.control.domain.model.playlist.PlaylistStats
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistStatsPort
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.quarkus.runtime.StartupEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlaylistMetricsTests {

  private val playlistStatsPort: PlaylistStatsPort = mockk()
  private val meterRegistry = SimpleMeterRegistry()
  private val metrics = PlaylistMetrics(playlistStatsPort, meterRegistry)

  @Test
  fun `gauges expose playlist stats counts`() {
    every { playlistStatsPort.current() } returns PlaylistStats(trackedCount = 5, outOfSyncCount = 2, pendingAlbumUpgradeCount = 1)

    metrics.onStartup(StartupEvent())

    assertThat(meterRegistry.find("app.playlist.tracked").gauge()?.value()).isEqualTo(5.0)
    assertThat(meterRegistry.find("app.playlist.out_of_sync").gauge()?.value()).isEqualTo(2.0)
    assertThat(meterRegistry.find("app.playlist.album_upgrade_pending").gauge()?.value()).isEqualTo(1.0)
  }

  @Test
  fun `all three gauges share a single port read per scrape`() {
    every { playlistStatsPort.current() } returns PlaylistStats(trackedCount = 5, outOfSyncCount = 2, pendingAlbumUpgradeCount = 1)

    metrics.onStartup(StartupEvent())
    meterRegistry.find("app.playlist.tracked").gauge()?.value()
    meterRegistry.find("app.playlist.out_of_sync").gauge()?.value()
    meterRegistry.find("app.playlist.album_upgrade_pending").gauge()?.value()

    verify(exactly = 1) { playlistStatsPort.current() }
  }
}
