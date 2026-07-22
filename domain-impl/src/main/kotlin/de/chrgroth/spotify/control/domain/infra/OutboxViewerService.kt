package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.infra.OutboxViewerPartition
import de.chrgroth.spotify.control.domain.port.`in`.infra.OutboxViewerPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxAdminPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class OutboxViewerService(
  private val tasksCache: OutboxViewerTasksCache,
  private val outboxAdmin: OutboxAdminPort,
) : OutboxViewerPort {

  override fun getPartitions(): List<OutboxViewerPartition> = tasksCache.current()

  override fun wipeAll() {
    logger.info { "Wiping all outbox events and partitions" }
    outboxAdmin.wipeAll()
    tasksCache.refresh()
  }

  override fun requeueStuckTasks(partitionKey: String): Int {
    logger.info { "Requeuing stuck tasks in outbox partition '$partitionKey'" }
    val clearedCount = outboxAdmin.requeueStuckTasks(partitionKey)
    tasksCache.refresh()
    return clearedCount
  }

  companion object : KLogging()
}
