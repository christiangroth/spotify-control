package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.`in`.OutboxViewerPartition
import de.chrgroth.spotify.control.domain.port.`in`.OutboxViewerPort
import de.chrgroth.spotify.control.domain.port.out.OutboxManagementPort
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

@ApplicationScoped
@Suppress("Unused")
class OutboxViewerAdapter(private val outboxManagement: OutboxManagementPort) : OutboxViewerPort {

    override fun getPartitions(): List<OutboxViewerPartition> = runBlocking {
        DomainOutboxPartition.all
            .map { partition -> partition to async(Dispatchers.IO) { outboxManagement.getTasksByPartition(partition.key) } }
            .map { (partition, deferred) -> OutboxViewerPartition(key = partition.key, tasks = deferred.await()) }
    }
}
