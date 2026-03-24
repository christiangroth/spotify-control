package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.`in`.OutboxViewerPartition
import de.chrgroth.spotify.control.domain.port.`in`.OutboxViewerPort
import de.chrgroth.spotify.control.domain.port.out.OutboxManagementPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class OutboxViewerAdapter(private val outboxManagement: OutboxManagementPort) : OutboxViewerPort {

    override fun getPartitions(): List<OutboxViewerPartition> =
        DomainOutboxPartition.all.map { partition ->
            OutboxViewerPartition(
                key = partition.key,
                tasks = outboxManagement.getTasksByPartition(partition.key),
            )
        }
}
