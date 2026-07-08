package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.infra.OutboxViewerPartition
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import io.quarkus.runtime.StartupEvent
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import mu.KLogging

// shared by every /outbox-viewer request so that the refresh-outbox-partitions SSE event, which fires on every
// outbox task lifecycle event, can never trigger more than one findByPartition scan per partition per refresh
// interval, no matter how many task events or open tabs are involved.
@ApplicationScoped
@Suppress("Unused", "UnusedParameter")
class OutboxViewerTasksCache(
  private val outbox: OutboxPort,
) {

  @Volatile
  private var cachedPartitions: List<OutboxViewerPartition> = emptyList()

  fun current(): List<OutboxViewerPartition> = cachedPartitions

  fun onStartup(@Observes event: StartupEvent) = refresh()

  @Scheduled(every = "15s")
  fun refresh() {
    try {
      cachedPartitions = runBlocking {
        DomainOutboxPartition.all
          .map { partition -> partition to async(Dispatchers.IO) { outbox.getTasksByPartition(partition.key) } }
          .map { (partition, deferred) -> OutboxViewerPartition(key = partition.key, tasks = deferred.await()) }
      }
    } catch (e: Exception) {
      logger.warn(e) { "Failed to refresh outbox viewer tasks, keeping previous values" }
    }
  }

  companion object : KLogging()
}
