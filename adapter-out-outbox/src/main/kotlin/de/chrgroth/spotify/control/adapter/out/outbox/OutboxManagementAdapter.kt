package de.chrgroth.spotify.control.adapter.out.outbox

import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxClient
import de.chrgroth.quarkus.outbox.domain.OutboxPartitionStatus
import de.chrgroth.spotify.control.domain.model.OutboxEventTypeCount
import de.chrgroth.spotify.control.domain.model.OutboxPartitionStats
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.OutboxManagementPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class OutboxManagementAdapter(
    private val client: ApplicationOutboxClient,
) : OutboxManagementPort {

    override fun getPartitionStats(): List<OutboxPartitionStats> {
        val partitionInfosByKey = client.partitionInfos().associateBy { it.key }
        return DomainOutboxPartition.all.map { partition ->
            val info = partitionInfosByKey[partition.key]
            OutboxPartitionStats(
                name = partition.key,
                status = info?.status?.name ?: OutboxPartitionStatus.ACTIVE.name,
                documentCount = info?.eventCount ?: 0L,
                blockedUntil = info?.pausedUntil,
                eventTypeCounts = info?.eventPerTypeCount
                    ?.entries
                    ?.map { (kClass, count) -> OutboxEventTypeCount(eventType = kClass.simpleName ?: "unknown", count = count) }
                    ?.sortedByDescending { it.count }
                    ?: emptyList(),
            )
        }
    }
}
