package de.chrgroth.spotify.control.adapter.out.outbox

import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxClient
import de.chrgroth.quarkus.outbox.domain.OutboxPartitionStatus
import de.chrgroth.spotify.control.domain.model.OutboxEventTypeCount
import de.chrgroth.spotify.control.domain.model.OutboxPartitionStats
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class OutboxPortAdapter(
    private val outbox: ApplicationOutboxClient,
) : OutboxPort {

    override fun enqueue(event: DomainOutboxEvent) {
        outbox.enqueue(event)
        logger.info { "Enqueued outbox event ${event.key} in partition ${event.partition.key}" }
    }

    override fun getPartitionStats(): List<OutboxPartitionStats> {
        val partitionInfosByKey = outbox.partitionInfos().associateBy { it.key }
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

    companion object : KLogging()
}
