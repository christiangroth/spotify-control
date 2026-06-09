package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class WipeCatalogArtistAlbumOutboxPartitionsStarter(
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
) : Starter {

  override val id = "WipeCatalogArtistAlbumOutboxPartitionsStarter-v1"

  override fun execute() {
    val db = mongoClient.getDatabase(databaseName)

    val eventsResult = db.getCollection(OUTBOX_COLLECTION)
      .deleteMany(Filters.`in`(PARTITION_FIELD, OLD_CATALOG_PARTITIONS))
    logger.info { "Deleted ${eventsResult.deletedCount} outbox event(s) from old catalog partitions: $OLD_CATALOG_PARTITIONS" }

    OLD_CATALOG_PARTITIONS.forEach { partition ->
      val partitionResult = db.getCollection(OUTBOX_PARTITIONS_COLLECTION)
        .deleteOne(Filters.eq(ID_FIELD, partition))
      logger.info { "Removed old outbox partition document '$partition': deleted=${partitionResult.deletedCount}" }
    }
  }

  companion object : KLogging() {
    private const val OUTBOX_COLLECTION = "outbox"
    private const val OUTBOX_PARTITIONS_COLLECTION = "outbox_partitions"
    private const val PARTITION_FIELD = "partition"
    private const val ID_FIELD = "_id"
    private val OLD_CATALOG_PARTITIONS = listOf("to-spotify-catalog-artist", "to-spotify-catalog-album")
  }
}
