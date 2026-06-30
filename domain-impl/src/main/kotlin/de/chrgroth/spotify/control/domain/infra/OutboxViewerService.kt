package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.model.infra.OutboxViewerPartition
import de.chrgroth.spotify.control.domain.port.`in`.infra.OutboxViewerPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxAdminPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class OutboxViewerService(
  private val outbox: OutboxPort,
  private val outboxAdmin: OutboxAdminPort,
) : OutboxViewerPort {

  override fun getPartitions(): List<OutboxViewerPartition> = runBlocking {
    DomainOutboxPartition.all
      .map { partition -> partition to async(Dispatchers.IO) { outbox.getTasksByPartition(partition.key) } }
      .map { (partition, deferred) -> OutboxViewerPartition(key = partition.key, tasks = deferred.await()) }
  }

  override fun wipeAll() {
    logger.info { "Wiping all outbox events and partitions" }
    outboxAdmin.wipeAll()
  }

  companion object : KLogging()
}
