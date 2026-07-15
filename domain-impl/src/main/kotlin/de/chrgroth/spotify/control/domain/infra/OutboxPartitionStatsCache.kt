package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.infra.OutboxPartitionStats
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxStatsPort
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

// implements OutboxStatsPort so adapter-in-http-metrics (which only depends on domain-api) can register
// gauges from these counts without querying the outbox library directly, following the same pattern as
// PlaylistStatsCache/CatalogStatsCache. Also shared directly with HealthService so neither gauge reads nor
// health-page/SSE-triggered requests ever query the outbox library directly; a single refresh serves every
// reader. Not delayed, so it anchors the stagger of the other every="15s" caches (see their delayed() values)
// against MongoDB.
@ApplicationScoped
@Suppress("Unused")
class OutboxPartitionStatsCache(
  private val outboxPort: OutboxPort,
) : OutboxStatsPort {

  @Volatile
  private var cachedStats: List<OutboxPartitionStats> = emptyList()

  override fun current(): List<OutboxPartitionStats> = cachedStats

  @Scheduled(every = "15s")
  fun refresh() {
    try {
      cachedStats = outboxPort.getPartitionStats()
    } catch (e: Exception) {
      logger.warn(e) { "Failed to refresh outbox partition stats, keeping previous values" }
    }
  }

  companion object : KLogging()
}
