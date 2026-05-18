package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class RemoveLegacyToSpotifyOutboxPartitionStarter(
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
) : Starter {

  override val id = "RemoveLegacyToSpotifyOutboxPartitionStarter-v1"

  override fun execute() {
    val result = mongoClient.getDatabase(databaseName)
      .getCollection(OUTBOX_PARTITIONS_COLLECTION)
      .deleteOne(Filters.eq(ID_FIELD, LEGACY_PARTITION))
    logger.info { "Removed legacy outbox partition '$LEGACY_PARTITION': deleted=${result.deletedCount}" }
  }

  companion object : KLogging() {
    private const val OUTBOX_PARTITIONS_COLLECTION = "outbox_partitions"
    private const val ID_FIELD = "_id"
    private const val LEGACY_PARTITION = "to-spotify"
  }
}