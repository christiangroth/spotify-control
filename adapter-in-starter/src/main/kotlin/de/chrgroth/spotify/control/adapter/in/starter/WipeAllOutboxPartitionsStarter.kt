package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.bson.Document
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class WipeAllOutboxPartitionsStarter(
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
) : Starter {

  override val id = "WipeAllOutboxPartitionsStarter-v1"

  override fun execute() {
    val db = mongoClient.getDatabase(databaseName)

    val eventsResult = db.getCollection(OUTBOX_COLLECTION).deleteMany(Document())
    logger.info { "Deleted ${eventsResult.deletedCount} outbox event(s) from all partitions" }

    val partitionsResult = db.getCollection(OUTBOX_PARTITIONS_COLLECTION).deleteMany(Document())
    logger.info { "Deleted ${partitionsResult.deletedCount} outbox partition document(s)" }
  }

  companion object : KLogging() {
    private const val OUTBOX_COLLECTION = "outbox"
    private const val OUTBOX_PARTITIONS_COLLECTION = "outbox_partitions"
  }
}
