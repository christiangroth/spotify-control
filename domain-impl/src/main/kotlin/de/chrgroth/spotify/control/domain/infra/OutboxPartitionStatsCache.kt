package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.infra.OutboxPartitionStats
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

// shared by OutboxMetrics and HealthService so that neither gauge reads nor health-page/SSE-triggered
// requests ever query the outbox library directly; a single refresh serves every reader.
@ApplicationScoped
@Suppress("Unused")
class OutboxPartitionStatsCache(
  private val outboxPort: OutboxPort,
) {

  @Volatile
  private var cachedStats: List<OutboxPartitionStats> = emptyList()

  fun current(): List<OutboxPartitionStats> = cachedStats

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
