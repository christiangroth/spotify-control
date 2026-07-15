package de.chrgroth.spotify.control.adapter.`in`.http.metrics

import de.chrgroth.spotify.control.domain.model.infra.MongoCollectionStats
import de.chrgroth.spotify.control.domain.port.out.infra.MongoCollectionStatsPort
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.quarkus.runtime.StartupEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MongoCollectionMetricsTests {

  private val mongoCollectionStats: MongoCollectionStatsPort = mockk()
  private val meterRegistry = SimpleMeterRegistry()
  private val metrics = MongoCollectionMetrics(mongoCollectionStats, meterRegistry)

  @Test
  fun `gauges expose size per collection`() {
    every { mongoCollectionStats.current() } returns listOf(
      MongoCollectionStats(name = "users", documentCount = 1L, size = 1024L),
    )

    metrics.onStartup(StartupEvent())

    val gauge = meterRegistry.find("mongodb.collection.size_bytes").tag("collection", "users").gauge()

    assertThat(gauge?.value()).isEqualTo(1024.0)
  }

  @Test
  fun `gauge reads delegate to the shared port on every evaluation`() {
    every { mongoCollectionStats.current() } returns listOf(
      MongoCollectionStats(name = "users", documentCount = 1L, size = 1024L),
      MongoCollectionStats(name = "playlists", documentCount = 2L, size = 2048L),
    )

    metrics.onStartup(StartupEvent())
    meterRegistry.meters.forEach { meter -> meter.measure().forEach { it.value } }

    verify(atLeast = 1) { mongoCollectionStats.current() }
  }
}
