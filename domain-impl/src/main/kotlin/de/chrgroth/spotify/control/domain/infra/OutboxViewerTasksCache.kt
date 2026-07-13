package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.infra.OutboxViewerPartition
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import mu.KLogging

// refreshed lazily on read with a 15s TTL so that concurrent /outbox-viewer requests (page load plus the
// refresh-outbox-partitions SSE-triggered snippet fetch) never trigger more than one findByPartition scan per
// partition per interval, and the outbox is not queried at all while nobody is viewing the page.
@ApplicationScoped
@Suppress("Unused")
class OutboxViewerTasksCache(
  private val outbox: OutboxPort,
) {

  @Volatile
  private var cachedPartitions: List<OutboxViewerPartition> = emptyList()

  @Volatile
  private var lastRefreshAtMs: Long = 0

  @Synchronized
  fun current(): List<OutboxViewerPartition> {
    if (System.currentTimeMillis() - lastRefreshAtMs >= REFRESH_INTERVAL_MS) {
      refresh()
    }
    return cachedPartitions
  }

  @Synchronized
  fun refresh() {
    try {
      cachedPartitions = runBlocking {
        DomainOutboxPartition.all
          .map { partition -> partition to async(Dispatchers.IO) { outbox.getTasksByPartition(partition.key) } }
          .map { (partition, deferred) -> OutboxViewerPartition(key = partition.key, tasks = deferred.await()) }
      }
    } catch (e: Exception) {
      logger.warn(e) { "Failed to refresh outbox viewer tasks, keeping previous values" }
    } finally {
      lastRefreshAtMs = System.currentTimeMillis()
    }
  }

  companion object : KLogging() {
    private const val REFRESH_INTERVAL_MS = 15_000L
  }
}
