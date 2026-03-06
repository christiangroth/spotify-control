package de.chrgroth.spotify.control.adapter.out.outbox

import de.chrgroth.spotify.control.domain.model.OutboxPartitionStats
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.OutboxInfoPort
import de.chrgroth.spotify.control.util.outbox.OutboxPartitionStatus
import de.chrgroth.spotify.control.util.outbox.OutboxRepository
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class OutboxInfoAdapter(
    private val repository: OutboxRepository,
) : OutboxInfoPort {

    override fun getPartitionStats(): List<OutboxPartitionStats> =
        DomainOutboxPartition.all.map { partition ->
            val info = repository.findPartition(partition)
            OutboxPartitionStats(
                name = partition.key,
                status = info?.status ?: OutboxPartitionStatus.ACTIVE.name,
                documentCount = repository.countByPartition(partition),
                blockedUntil = info?.pausedUntil,
            )
        }
}
