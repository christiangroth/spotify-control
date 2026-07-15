package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.bson.Document
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class WipeOutboxArchiveStarter(
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
) : Starter {

  override val id = "WipeOutboxArchiveStarter-v1"

  override fun execute() {
    val db = mongoClient.getDatabase(databaseName)

    val result = db.getCollection(OUTBOX_ARCHIVE_COLLECTION).deleteMany(Document())
    logger.info { "Deleted ${result.deletedCount} outbox archive entry/entries" }
  }

  companion object : KLogging() {
    private const val OUTBOX_ARCHIVE_COLLECTION = "outbox_archive"
  }
}
