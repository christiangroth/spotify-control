package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.infra.MongoCollectionStats
import de.chrgroth.spotify.control.domain.port.out.infra.MongoStatsPort
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.quarkus.runtime.StartupEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MongoCollectionMetricsTests {

  private val mongoStats: MongoStatsPort = mockk()
  private val meterRegistry = SimpleMeterRegistry()
  private val metrics = MongoCollectionMetrics(mongoStats, meterRegistry)

  @Test
  fun `gauges expose size per collection`() {
    every { mongoStats.getCollectionStats() } returns listOf(
      MongoCollectionStats(name = "users", documentCount = 1L, size = 1024L),
    )

    metrics.onStartup(StartupEvent())

    val gauge = meterRegistry.find("mongodb.collection.size_bytes").tag("collection", "users").gauge()

    assertThat(gauge?.value()).isEqualTo(1024.0)
  }

  @Test
  fun `collection stats are shared across all gauge reads instead of re-queried per collection`() {
    every { mongoStats.getCollectionStats() } returns listOf(
      MongoCollectionStats(name = "users", documentCount = 1L, size = 1024L),
      MongoCollectionStats(name = "playlists", documentCount = 2L, size = 2048L),
    )

    metrics.onStartup(StartupEvent())
    meterRegistry.meters.forEach { meter -> meter.measure().forEach { it.value } }

    verify(exactly = 1) { mongoStats.getCollectionStats() }
  }

  @Test
  fun `a failed refresh keeps the previously cached values instead of propagating`() {
    every { mongoStats.getCollectionStats() } returns listOf(
      MongoCollectionStats(name = "users", documentCount = 1L, size = 1024L),
    )
    metrics.onStartup(StartupEvent())

    every { mongoStats.getCollectionStats() } throws IllegalStateException("mongo unreachable")
    metrics.refresh()

    val gauge = meterRegistry.find("mongodb.collection.size_bytes").tag("collection", "users").gauge()
    assertThat(gauge?.value()).isEqualTo(1024.0)
  }
}
