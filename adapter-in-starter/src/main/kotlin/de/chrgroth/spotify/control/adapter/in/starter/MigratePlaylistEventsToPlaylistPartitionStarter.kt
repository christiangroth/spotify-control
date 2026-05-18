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
class MigratePlaylistEventsToPlaylistPartitionStarter(
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
) : Starter {

  override val id = "MigratePlaylistEventsToPlaylistPartitionStarter-v1"

  override fun execute() {
    val collection = mongoClient.getDatabase(databaseName).getCollection(OUTBOX_COLLECTION)

    val playlistResult = collection.updateMany(
      Filters.and(
        Filters.eq(PARTITION_FIELD, LEGACY_PARTITION),
        Filters.`in`(EVENT_TYPE_FIELD, PLAYLIST_EVENT_TYPES),
      ),
      Updates.set(PARTITION_FIELD, PLAYLIST_PARTITION),
    )
    logger.info { "Migrated ${playlistResult.modifiedCount} playlist outbox event(s) from '$LEGACY_PARTITION' to '$PLAYLIST_PARTITION'" }

    val userResult = collection.updateMany(
      Filters.and(
        Filters.eq(PARTITION_FIELD, LEGACY_PARTITION),
        Filters.`in`(EVENT_TYPE_FIELD, USER_EVENT_TYPES),
      ),
      Updates.set(PARTITION_FIELD, USER_PARTITION),
    )
    logger.info { "Migrated ${userResult.modifiedCount} user outbox event(s) from '$LEGACY_PARTITION' to '$USER_PARTITION'" }

    val catalogResult = collection.updateMany(
      Filters.eq(PARTITION_FIELD, LEGACY_PARTITION),
      Updates.set(PARTITION_FIELD, CATALOG_PARTITION),
    )
    logger.info { "Migrated ${catalogResult.modifiedCount} catalog outbox event(s) from '$LEGACY_PARTITION' to '$CATALOG_PARTITION'" }
  }

  companion object : KLogging() {
    private const val OUTBOX_COLLECTION = "outbox"
    private const val PARTITION_FIELD = "partition"
    private const val EVENT_TYPE_FIELD = "eventType"
    private const val LEGACY_PARTITION = "to-spotify"
    private const val PLAYLIST_PARTITION = "to-spotify-playlist"
    private const val USER_PARTITION = "to-spotify-user"
    private const val CATALOG_PARTITION = "to-spotify-catalog"
    private val PLAYLIST_EVENT_TYPES = listOf("SyncPlaylistInfo", "SyncPlaylistData")
    private val USER_EVENT_TYPES = listOf("UpdateUserProfile")
  }
}
