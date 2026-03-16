package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.bson.Document
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class DeleteCatalogDataStarter(
    private val mongoClient: MongoClient,
    @param:ConfigProperty(name = "quarkus.mongodb.database")
    private val databaseName: String,
) : Starter {

    override val id = "DeleteCatalogDataStarter-v1"

    override fun execute() {
        val database = mongoClient.getDatabase(databaseName)
        CATALOG_COLLECTIONS.forEach { collection ->
            val result = database.getCollection(collection).deleteMany(Document())
            logger.info { "Deleted ${result.deletedCount} documents from $collection" }
        }
    }

    companion object : KLogging() {
        private val CATALOG_COLLECTIONS = listOf(
            "app_track",
            "app_album",
            "app_artist",
            "app_playlist_check",
            "spotify_playlist",
            "outbox",
        )
    }
}
