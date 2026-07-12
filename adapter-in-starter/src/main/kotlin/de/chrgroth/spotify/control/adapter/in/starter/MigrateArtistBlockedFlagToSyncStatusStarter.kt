package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import de.chrgroth.quarkus.starters.domain.Starter
import de.chrgroth.spotify.control.domain.model.catalog.ArtistSyncStatus
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

// Migrates app_artist.blockedFromAggregation (Boolean) to the new syncStatus field (see #748 Shallow Artists).
// Assumption statuses are reserved for artists newly discovered after this feature ships (see
// CatalogService.syncArtistDetails); every artist that already exists in the catalog at migration time was already
// being treated as fully syncable (blockedFromAggregation only ever filtered aggregation, never catalog sync), so
// it is migrated straight to its definitive status: SHALLOW if it was previously blocked, SYNC otherwise.
@ApplicationScoped
@Suppress("Unused")
class MigrateArtistBlockedFlagToSyncStatusStarter(
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
) : Starter {

  override val id = "MigrateArtistBlockedFlagToSyncStatusStarter-v1"

  override fun execute() {
    val collection = mongoClient.getDatabase(databaseName).getCollection(APP_ARTIST_COLLECTION)

    val blockedResult = collection.updateMany(
      Filters.eq(BLOCKED_FROM_AGGREGATION_FIELD, true),
      Updates.combine(
        Updates.set(SYNC_STATUS_FIELD, ArtistSyncStatus.SHALLOW.name),
        Updates.unset(BLOCKED_FROM_AGGREGATION_FIELD),
      ),
    )
    logger.info { "Migrated ${blockedResult.modifiedCount} previously blocked artist(s) to ${ArtistSyncStatus.SHALLOW}" }

    val remainingResult = collection.updateMany(
      Filters.exists(SYNC_STATUS_FIELD, false),
      Updates.combine(
        Updates.set(SYNC_STATUS_FIELD, ArtistSyncStatus.SYNC.name),
        Updates.unset(BLOCKED_FROM_AGGREGATION_FIELD),
      ),
    )
    logger.info { "Migrated ${remainingResult.modifiedCount} artist(s) to ${ArtistSyncStatus.SYNC}" }
  }

  companion object : KLogging() {
    private const val APP_ARTIST_COLLECTION = "app_artist"
    private const val BLOCKED_FROM_AGGREGATION_FIELD = "blockedFromAggregation"
    private const val SYNC_STATUS_FIELD = "syncStatus"
  }
}
