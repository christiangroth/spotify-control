package de.chrgroth.spotify.control.adapter.out.outbox

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxClient
import de.chrgroth.quarkus.outbox.domain.OutboxControllerAdapter
import de.chrgroth.quarkus.outbox.domain.OutboxPartitionStatus
import de.chrgroth.spotify.control.domain.model.OutboxEventTypeCount
import de.chrgroth.spotify.control.domain.model.OutboxPartitionStats
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.OutboxManagementPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.bson.Document
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class OutboxManagementAdapter(
    private val client: ApplicationOutboxClient,
    private val outboxController: OutboxControllerAdapter,
    private val mongoClient: MongoClient,
    @param:ConfigProperty(name = "quarkus.mongodb.database")
    private val databaseName: String,
) : OutboxManagementPort {

    override fun getPartitionStats(): List<OutboxPartitionStats> {
        val partitionInfosByKey = client.partitionInfos().associateBy { it.key }
        return DomainOutboxPartition.all.map { partition ->
            val info = partitionInfosByKey[partition.key]
            OutboxPartitionStats(
                name = partition.key,
                status = info?.status?.name ?: OutboxPartitionStatus.ACTIVE.name,
                documentCount = countByPartition(partition.key),
                blockedUntil = info?.pausedUntil,
                eventTypeCounts = queryEventTypeCounts(partition.key),
            )
        }
    }

    override fun activate(partitionKey: String): Boolean {
        val partition = DomainOutboxPartition.all.firstOrNull { it.key == partitionKey } ?: return false
        outboxController.activatePartition(partition)
        return true
    }

    override fun deleteByEventTypes(eventTypeKeys: List<String>) {
        if (eventTypeKeys.isEmpty()) return
        val result = mongoClient.getDatabase(databaseName)
            .getCollection(OUTBOX_COLLECTION)
            .deleteMany(Filters.`in`("eventType", eventTypeKeys))
        logger.info { "Deleted ${result.deletedCount} outbox tasks for event types: ${eventTypeKeys.joinToString()}" }
    }

    private fun countByPartition(partitionKey: String): Long =
        mongoClient.getDatabase(databaseName)
            .getCollection(OUTBOX_COLLECTION)
            .countDocuments(Filters.eq("partition", partitionKey))

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
