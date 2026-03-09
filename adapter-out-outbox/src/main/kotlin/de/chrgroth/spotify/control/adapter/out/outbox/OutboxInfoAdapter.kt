package de.chrgroth.spotify.control.adapter.out.outbox

import com.mongodb.client.MongoClient
import de.chrgroth.outbox.OutboxPartitionStatus
import de.chrgroth.outbox.OutboxRepository
import de.chrgroth.spotify.control.domain.model.OutboxEventTypeCount
import de.chrgroth.spotify.control.domain.model.OutboxPartitionStats
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.OutboxInfoPort
import jakarta.enterprise.context.ApplicationScoped
import org.bson.Document
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class OutboxInfoAdapter(
    private val repository: OutboxRepository,
    private val mongoClient: MongoClient,
    @param:ConfigProperty(name = "quarkus.mongodb.database")
    private val databaseName: String,
) : OutboxInfoPort {

    override fun getPartitionStats(): List<OutboxPartitionStats> =
        DomainOutboxPartition.all.map { partition ->
            val info = repository.findPartition(partition)
            OutboxPartitionStats(
                name = partition.key,
                status = info?.status ?: OutboxPartitionStatus.ACTIVE.name,
                documentCount = repository.countByPartition(partition),
                blockedUntil = info?.pausedUntil,
                eventTypeCounts = queryEventTypeCounts(partition.key),
            )
        }

    private fun queryEventTypeCounts(partitionKey: String): List<OutboxEventTypeCount> =
        mongoClient.getDatabase(databaseName)
            .getCollection(OUTBOX_COLLECTION)
            .aggregate(
                listOf(
                    Document("\$match", Document("partition", partitionKey)),
                    Document("\$group", Document("_id", "\$eventType").append("count", Document("\$sum", 1))),
                    Document("\$sort", Document("count", -1)),
                ),
            )
            .map { OutboxEventTypeCount(eventType = it.getString("_id"), count = it.getInteger("count").toLong()) }
            .toList()

    companion object {
        private const val OUTBOX_COLLECTION = "outbox"
    }
}
