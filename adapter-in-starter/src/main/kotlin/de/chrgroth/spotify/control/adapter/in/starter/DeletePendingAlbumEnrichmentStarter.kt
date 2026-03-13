package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import de.chrgroth.quarkus.starters.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class DeletePendingAlbumEnrichmentStarter(
    private val mongoClient: MongoClient,
    @param:ConfigProperty(name = "quarkus.mongodb.database")
    private val databaseName: String,
) : Starter {

    override val id = "DeletePendingAlbumEnrichmentStarter-v1"

    override fun execute() {
        val result = mongoClient.getDatabase(databaseName)
            .getCollection(OUTBOX_COLLECTION)
            .deleteMany(Filters.eq("eventType", ENRICH_ALBUM_DETAILS_KEY))
        logger.info { "Deleted ${result.deletedCount} pending EnrichAlbumDetails outbox tasks" }
    }

    companion object : KLogging() {
        private const val OUTBOX_COLLECTION = "outbox"
        private const val ENRICH_ALBUM_DETAILS_KEY = "EnrichAlbumDetails"
    }
}
