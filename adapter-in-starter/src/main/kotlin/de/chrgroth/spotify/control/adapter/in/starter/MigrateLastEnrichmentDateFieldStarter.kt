package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import de.chrgroth.quarkus.starters.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class MigrateLastEnrichmentDateFieldStarter(
    private val mongoClient: MongoClient,
    @param:ConfigProperty(name = "quarkus.mongodb.database")
    private val databaseName: String,
) : Starter {

    override val id = "MigrateLastEnrichmentDateFieldStarter-v1"

    override fun execute() {
        listOf(ARTIST_COLLECTION, ALBUM_COLLECTION, TRACK_COLLECTION).forEach { collection ->
            val result = mongoClient.getDatabase(databaseName)
                .getCollection(collection)
                .updateMany(
                    Filters.exists(OLD_FIELD),
                    Updates.rename(OLD_FIELD, NEW_FIELD),
                )
            logger.info { "Migrated ${result.modifiedCount} documents in '$collection': renamed '$OLD_FIELD' to '$NEW_FIELD'" }
        }
    }

    companion object : KLogging() {
        private const val ARTIST_COLLECTION = "app_artist"
        private const val ALBUM_COLLECTION = "app_album"
        private const val TRACK_COLLECTION = "app_track"
        private const val OLD_FIELD = "lastEnrichmentDate"
        private const val NEW_FIELD = "lastSync"
    }
}
