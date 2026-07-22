package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxAdminPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.bson.Document
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Instant

@ApplicationScoped
@Suppress("Unused")
class OutboxAdminPortAdapter(
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
) : OutboxAdminPort {

  override fun wipeAll() {
    val db = mongoClient.getDatabase(databaseName)

    val eventsResult = db.getCollection(OUTBOX_COLLECTION).deleteMany(Document())
    logger.info { "Deleted ${eventsResult.deletedCount} outbox event(s) from all partitions" }

    val partitionsResult = db.getCollection(OUTBOX_PARTITIONS_COLLECTION).deleteMany(Document())
    logger.info { "Deleted ${partitionsResult.deletedCount} outbox partition document(s)" }
  }

  override fun requeueStuckTasks(partitionKey: String): Int {
    val result = mongoClient.getDatabase(databaseName).getCollection(OUTBOX_COLLECTION).deleteMany(
      Filters.and(
        Filters.eq("partition", partitionKey),
        Filters.eq("status", "PENDING"),
        Filters.ne("nextRetryAt", null),
        Filters.lte("nextRetryAt", Instant.now()),
      ),
    )
    val clearedCount = result.deletedCount.toInt()
    logger.info { "Requeued $clearedCount stuck task(s) in outbox partition '$partitionKey'" }
    return clearedCount
  }

  companion object : KLogging() {
    private const val OUTBOX_COLLECTION = "outbox"
    private const val OUTBOX_PARTITIONS_COLLECTION = "outbox_partitions"
  }
}
