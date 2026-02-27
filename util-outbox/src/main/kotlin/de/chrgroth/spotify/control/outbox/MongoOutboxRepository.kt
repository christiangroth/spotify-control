package de.chrgroth.spotify.control.outbox

import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import mu.KLogging
import java.time.Instant as JavaInstant
import java.util.UUID

@ApplicationScoped
class MongoOutboxRepository : OutboxRepository {

    override fun enqueue(partition: String, eventType: String, payload: String) {
        val now = JavaInstant.now()
        val doc = OutboxDocument().apply {
            id = UUID.randomUUID().toString()
            this.partition = partition
            this.eventType = eventType
            this.payload = payload
            status = OutboxTaskStatus.PENDING.name
            attempts = 0
            createdAt = now
            updatedAt = now
        }
        doc.persist()
        logger.debug { "Enqueued outbox task: partition=$partition eventType=$eventType id=${doc.id}" }
    }

    override fun claim(partition: String): OutboxTask? {
        val now = JavaInstant.now()
        val filter = Filters.and(
            Filters.eq("partition", partition),
            Filters.eq("status", OutboxTaskStatus.PENDING.name),
            Filters.or(
                Filters.exists("nextRetryAt", false),
                Filters.eq("nextRetryAt", null),
                Filters.lte("nextRetryAt", now),
            ),
        )
        val update = Updates.combine(
            Updates.set("status", OutboxTaskStatus.PROCESSING.name),
            Updates.set("updatedAt", now),
        )
        val options = FindOneAndUpdateOptions()
            .sort(Sorts.ascending("createdAt"))
            .returnDocument(ReturnDocument.AFTER)
        val doc = OutboxDocument.mongoCollection().findOneAndUpdate(filter, update, options)
            ?: return null
        return doc.toTask()
    }

    override fun complete(task: OutboxTask) {
        val now = JavaInstant.now()
        val archive = OutboxArchiveDocument().apply {
            id = task.id
            partition = task.partition
            eventType = task.eventType
            payload = task.payload
            status = OutboxTaskStatus.DONE.name
            attempts = task.attempts
            createdAt = task.createdAt.toJavaInstant()
            updatedAt = now
            nextRetryAt = task.nextRetryAt?.toJavaInstant()
            lastError = task.lastError
            completedAt = now
        }
        archive.persist()
        OutboxDocument.deleteById(task.id)
        logger.debug { "Completed outbox task: id=${task.id}" }
    }

    override fun fail(task: OutboxTask, error: String, nextRetryAt: Instant?) {
        val now = JavaInstant.now()
        val newStatus = if (nextRetryAt == null) OutboxTaskStatus.FAILED.name else OutboxTaskStatus.PENDING.name
        val update = Updates.combine(
            Updates.set("status", newStatus),
            Updates.set("attempts", task.attempts + 1),
            Updates.set("updatedAt", now),
            Updates.set("lastError", error),
            Updates.set("nextRetryAt", nextRetryAt?.toJavaInstant()),
        )
        OutboxDocument.mongoCollection().updateOne(Filters.eq("_id", task.id), update)
        logger.debug { "Failed outbox task: id=${task.id} newStatus=$newStatus nextRetryAt=$nextRetryAt" }
    }

    override fun resetStaleProcessing() {
        val now = JavaInstant.now()
        val filter = Filters.eq("status", OutboxTaskStatus.PROCESSING.name)
        val update = Updates.combine(
            Updates.set("status", OutboxTaskStatus.PENDING.name),
            Updates.set("updatedAt", now),
            Updates.set("nextRetryAt", now),
        )
        val result = OutboxDocument.mongoCollection().updateMany(filter, update)
        if (result.modifiedCount > 0) {
            logger.info { "Reset ${result.modifiedCount} stale PROCESSING outbox task(s) to PENDING" }
        }
    }

    private fun OutboxDocument.toTask() = OutboxTask(
        id = id,
        partition = partition,
        eventType = eventType,
        payload = payload,
        status = OutboxTaskStatus.valueOf(status),
        attempts = attempts,
        createdAt = createdAt.toKotlinInstant(),
        updatedAt = updatedAt.toKotlinInstant(),
        nextRetryAt = nextRetryAt?.toKotlinInstant(),
        lastError = lastError,
    )

    companion object : KLogging()
}
