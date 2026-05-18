package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class MigrateCatalogEventsToNewPartitionsStarter(
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
) : Starter {

  override val id = "MigrateCatalogEventsToNewPartitionsStarter-v1"

  override fun execute() {
    val collection = mongoClient.getDatabase(databaseName).getCollection(OUTBOX_COLLECTION)

    val artistResult = collection.updateMany(
      Filters.and(
        Filters.eq(PARTITION_FIELD, LEGACY_CATALOG_PARTITION),
        Filters.`in`(EVENT_TYPE_FIELD, ARTIST_EVENT_TYPES),
      ),
      Updates.set(PARTITION_FIELD, ARTIST_PARTITION),
    )
    logger.info { "Migrated ${artistResult.modifiedCount} artist outbox event(s) from '$LEGACY_CATALOG_PARTITION' to '$ARTIST_PARTITION'" }

    val albumResult = collection.updateMany(
      Filters.and(
        Filters.eq(PARTITION_FIELD, LEGACY_CATALOG_PARTITION),
        Filters.`in`(EVENT_TYPE_FIELD, ALBUM_EVENT_TYPES),
      ),
      Updates.set(PARTITION_FIELD, ALBUM_PARTITION),
    )
    logger.info { "Migrated ${albumResult.modifiedCount} album outbox event(s) from '$LEGACY_CATALOG_PARTITION' to '$ALBUM_PARTITION'" }
  }

  companion object : KLogging() {
    private const val OUTBOX_COLLECTION = "outbox"
    private const val PARTITION_FIELD = "partition"
    private const val EVENT_TYPE_FIELD = "eventType"
    private const val LEGACY_CATALOG_PARTITION = "to-spotify-catalog"
    private const val ARTIST_PARTITION = "to-spotify-catalog-artist"
    private const val ALBUM_PARTITION = "to-spotify-catalog-album"
    private val ARTIST_EVENT_TYPES = listOf("SyncArtistDetails", "EnrichArtistDetails", "SyncArtistAlbums")
    private val ALBUM_EVENT_TYPES = listOf("SyncAlbumDetails")
  }
}
