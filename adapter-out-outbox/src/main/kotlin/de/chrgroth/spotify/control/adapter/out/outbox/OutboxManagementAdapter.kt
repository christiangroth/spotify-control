package de.chrgroth.spotify.control.adapter.out.outbox

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import de.chrgroth.outbox.Outbox
import de.chrgroth.outbox.OutboxPartitionStatus
import de.chrgroth.outbox.OutboxRepository
import de.chrgroth.spotify.control.domain.model.OutboxEventTypeCount
import de.chrgroth.spotify.control.domain.model.OutboxPartitionStats
import de.chrgroth.spotify.control.domain.model.OutboxTask
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.OutboxManagementPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.bson.Document
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Instant

@ApplicationScoped
@Suppress("Unused")
class OutboxManagementAdapter(
    private val repository: OutboxRepository,
    private val outbox: Outbox,
    private val mongoClient: MongoClient,
    @param:ConfigProperty(name = "quarkus.mongodb.database")
    private val databaseName: String,
) : OutboxManagementPort {

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

    override fun getTasksByPartition(partitionKey: String): List<OutboxTask> =
        mongoClient.getDatabase(databaseName)
            .getCollection(OUTBOX_COLLECTION)
            .find(Filters.eq("partition", partitionKey))
            .map { doc ->
                OutboxTask(
                    eventType = doc.getString("eventType") ?: "",
                    deduplicationKey = doc.getString("deduplicationKey") ?: "",
                    priority = doc.getString("priority") ?: "NORMAL",
                    status = doc.getString("status") ?: "PENDING",
                    attempts = doc.getInteger("attempts", 0),
                    nextRetryAt = doc.getDate("nextRetryAt")?.toInstant(),
                    createdAt = doc.getDate("createdAt")?.toInstant() ?: Instant.EPOCH,
                    lastError = doc.getString("lastError"),
                )
            }
            .toList()
            .sortedWith(compareBy({ it.priorityOrder }, { it.nextRetryAt ?: Instant.MAX }))

    override fun activate(partitionKey: String): Boolean {
        val partition = DomainOutboxPartition.all.firstOrNull { it.key == partitionKey } ?: return false
        outbox.activatePartition(partition)
        outbox.signal(partition)
        return true
    }

    override fun deleteByEventTypes(eventTypeKeys: List<String>) {
        if (eventTypeKeys.isEmpty()) return
        val result = mongoClient.getDatabase(databaseName)
            .getCollection(OUTBOX_COLLECTION)
            .deleteMany(Filters.`in`("eventType", eventTypeKeys))
        logger.info { "Deleted ${result.deletedCount} outbox tasks for event types: ${eventTypeKeys.joinToString()}" }
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

    companion object : KLogging() {
        private const val OUTBOX_COLLECTION = "outbox"
    }
}
