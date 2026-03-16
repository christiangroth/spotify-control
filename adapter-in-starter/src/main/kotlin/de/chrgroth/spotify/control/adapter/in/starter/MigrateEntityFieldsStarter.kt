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
class MigrateEntityFieldsStarter(
    private val mongoClient: MongoClient,
    @param:ConfigProperty(name = "quarkus.mongodb.database")
    private val databaseName: String,
) : Starter {

    override val id = "MigrateEntityFieldsStarter-v1"

    override fun execute() {
        migrateTrackTitle()
        migrateAlbumTitle()
    }

    private fun migrateTrackTitle() {
        val collection = mongoClient.getDatabase(databaseName).getCollection(TRACK_COLLECTION)
        val result = collection.updateMany(
            Filters.exists(TRACK_TITLE_OLD_FIELD),
            Updates.rename(TRACK_TITLE_OLD_FIELD, TITLE_FIELD),
        )
        logger.info { "Migrated ${result.modifiedCount} tracks: renamed '$TRACK_TITLE_OLD_FIELD' to '$TITLE_FIELD'" }
    }

    private fun migrateAlbumTitle() {
        val collection = mongoClient.getDatabase(databaseName).getCollection(ALBUM_COLLECTION)
        val result = collection.updateMany(
            Filters.exists(ALBUM_TITLE_OLD_FIELD),
            Updates.rename(ALBUM_TITLE_OLD_FIELD, TITLE_FIELD),
        )
        logger.info { "Migrated ${result.modifiedCount} albums: renamed '$ALBUM_TITLE_OLD_FIELD' to '$TITLE_FIELD'" }
    }

    companion object : KLogging() {
        private const val TRACK_COLLECTION = "app_track"
        private const val ALBUM_COLLECTION = "app_album"
        private const val TITLE_FIELD = "title"
        private const val TRACK_TITLE_OLD_FIELD = "trackTitle"
        private const val ALBUM_TITLE_OLD_FIELD = "albumTitle"
    }
}
