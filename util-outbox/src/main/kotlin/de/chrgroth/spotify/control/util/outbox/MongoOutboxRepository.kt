package de.chrgroth.spotify.control.util.outbox

import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class MongoOutboxRepository : OutboxRepository {

    override fun claim(partition: OutboxPartition): OutboxTask? {
        val partitionDoc = findPartition(partition)
        if (partitionDoc != null && partitionDoc.status == OutboxPartitionStatus.PAUSED.name) {
            return null
        }

        val now = Instant.now()
        val doc = OutboxDocument.mongoCollection().findOneAndUpdate(
            Filters.and(
                Filters.eq("partition", partition.key),
                Filters.eq("status", OutboxTaskStatus.PENDING.name),
                Filters.or(
                    Filters.exists("nextRetryAt", false),
                    Filters.eq("nextRetryAt", null),
                    Filters.lte("nextRetryAt", now),
                ),
            ),
            Updates.combine(
                Updates.set("status", OutboxTaskStatus.PROCESSING.name),
                Updates.set("updatedAt", now),
            ),
            FindOneAndUpdateOptions()
                .sort(Sorts.orderBy(Sorts.ascending("priority"), Sorts.ascending("createdAt")))
                .returnDocument(ReturnDocument.AFTER),
        ) ?: return null

        return doc.toTask()
    }

    override fun complete(task: OutboxTask) {
        val now = Instant.now()
        val archiveDoc = OutboxArchiveDocument().apply {
            id = task.id
            partition = task.partition
            eventType = task.eventType
            deduplicationKey = task.deduplicationKey
            payload = task.payload
            status = OutboxTaskStatus.DONE.name
            attempts = task.attempts
            createdAt = task.createdAt
            updatedAt = now
            nextRetryAt = task.nextRetryAt
            priority = task.priority.name
            lastError = task.lastError
            completedAt = now
        }
        archiveDoc.persist()
        OutboxDocument.deleteById(task.id)
    }

    override fun fail(task: OutboxTask, error: String, nextRetryAt: Instant?) {
        val now = Instant.now()
        val newStatus = if (nextRetryAt == null) OutboxTaskStatus.FAILED.name else OutboxTaskStatus.PENDING.name
        val updates = mutableListOf(
            Updates.set("status", newStatus),
            Updates.inc("attempts", 1),
            Updates.set("updatedAt", now),
            Updates.set("lastError", error),
        )
        if (nextRetryAt != null) {
            updates.add(Updates.set("nextRetryAt", nextRetryAt))
        } else {
            updates.add(Updates.unset("nextRetryAt"))
        }
        OutboxDocument.mongoCollection().updateOne(
            Filters.eq("_id", task.id),
            Updates.combine(updates),
        )
    }

    override fun reschedule(task: OutboxTask, nextRetryAt: Instant) {
        val now = Instant.now()
        OutboxDocument.mongoCollection().updateOne(
            Filters.eq("_id", task.id),
            Updates.combine(
                Updates.set("status", OutboxTaskStatus.PENDING.name),
                Updates.set("updatedAt", now),
                Updates.set("nextRetryAt", nextRetryAt),
            ),
        )
    }

    override fun enqueue(
        partition: OutboxPartition,
        event: OutboxEvent,
        payload: String,
        priority: OutboxTaskPriority,
    ): Boolean {
        val deduplicationKey = event.deduplicationKey()
        val existing = OutboxDocument.mongoCollection().find(
            Filters.and(
                Filters.eq("partition", partition.key),
                Filters.eq("deduplicationKey", deduplicationKey),
                Filters.`in`("status", OutboxTaskStatus.PENDING.name, OutboxTaskStatus.PROCESSING.name),
            ),
        ).first()

        if (existing != null) {
            logger.debug { "Skipping duplicate outbox task: partition=${partition.key}, deduplicationKey=$deduplicationKey" }
            return false
        }

        val now = Instant.now()
        val doc = OutboxDocument().apply {
            id = UUID.randomUUID().toString()
            this.partition = partition.key
            this.eventType = event.key
            this.deduplicationKey = deduplicationKey
            this.payload = payload
            status = OutboxTaskStatus.PENDING.name
            attempts = 0
            createdAt = now
            updatedAt = now
            nextRetryAt = null
            this.priority = priority.name
            lastError = null
        }
        doc.persist()
        return true
    }

    override fun pausePartition(partition: OutboxPartition, reason: String, pausedUntil: Instant) {
        OutboxPartitionDocument.mongoCollection().findOneAndUpdate(
            Filters.eq("_id", partition.key),
            Updates.combine(
                Updates.set("status", OutboxPartitionStatus.PAUSED.name),
                Updates.set("statusReason", reason),
                Updates.set("pausedUntil", pausedUntil),
            ),
            FindOneAndUpdateOptions().upsert(true),
        )
    }

    override fun activatePartition(partition: OutboxPartition) {
        OutboxPartitionDocument.mongoCollection().findOneAndUpdate(
            Filters.eq("_id", partition.key),
            Updates.combine(
                Updates.set("status", OutboxPartitionStatus.ACTIVE.name),
                Updates.unset("statusReason"),
                Updates.unset("pausedUntil"),
            ),
            FindOneAndUpdateOptions().upsert(true),
        )
    }

    override fun findPartition(partition: OutboxPartition): OutboxPartitionInfo? =
        OutboxPartitionDocument.findById(partition.key)?.toInfo()

    private fun OutboxPartitionDocument.toInfo() = OutboxPartitionInfo(
        key = partitionKey,
        status = status,
        statusReason = statusReason,
        pausedUntil = pausedUntil,
    )

    /** Resets all PROCESSING tasks back to PENDING. Should be called at application startup to recover tasks that were interrupted mid-processing. */
    override fun resetStaleProcessingTasks() {
        val now = Instant.now()
        val result = OutboxDocument.mongoCollection().updateMany(
            Filters.eq("status", OutboxTaskStatus.PROCESSING.name),
            Updates.combine(
                Updates.set("status", OutboxTaskStatus.PENDING.name),
                Updates.set("nextRetryAt", now),
                Updates.set("updatedAt", now),
            ),
        )
        if (result.modifiedCount > 0) {
            logger.info { "Reset ${result.modifiedCount} stale PROCESSING tasks back to PENDING" }
        }
    }

    override fun countByPartition(partition: OutboxPartition): Long =
        OutboxDocument.count("partition = ?1", partition.key)

    private fun OutboxDocument.toTask() = OutboxTask(
        id = id,
        partition = partition,
        eventType = eventType,
        payload = payload,
        deduplicationKey = deduplicationKey,
        status = OutboxTaskStatus.valueOf(status),
        attempts = attempts,
        createdAt = createdAt,
        updatedAt = updatedAt,
        nextRetryAt = nextRetryAt,
        priority = OutboxTaskPriority.valueOf(priority),
        lastError = lastError,
    )

    companion object : KLogging()
}
