package de.chrgroth.spotify.control.adapter.out.outbox

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxClient
import de.chrgroth.quarkus.outbox.domain.OutboxPartitionStatus
import de.chrgroth.spotify.control.domain.model.OutboxEventTypeCount
import de.chrgroth.spotify.control.domain.model.OutboxPartitionStats
import de.chrgroth.spotify.control.domain.model.OutboxTask
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Instant

@ApplicationScoped
@Suppress("Unused")
class OutboxPortAdapter(
  private val outbox: ApplicationOutboxClient,
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
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


  companion object : KLogging() {
    private const val OUTBOX_COLLECTION = "outbox"
  }
}
