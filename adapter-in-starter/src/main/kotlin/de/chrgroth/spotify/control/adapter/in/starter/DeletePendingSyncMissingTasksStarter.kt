package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import de.chrgroth.quarkus.starters.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class DeletePendingSyncMissingTasksStarter(
    private val mongoClient: MongoClient,
    @param:ConfigProperty(name = "quarkus.mongodb.database")
    private val databaseName: String,
) : Starter {

    override val id = "DeletePendingSyncMissingTasksStarter-v2"

    override fun execute() {
        val outboxResult = mongoClient.getDatabase(databaseName)
            .getCollection(OUTBOX_COLLECTION)
            .deleteMany(Filters.`in`("eventType", REMOVED_SYNC_KEYS))
        logger.info { "Deleted ${outboxResult.deletedCount} legacy sync outbox tasks (${REMOVED_SYNC_KEYS.joinToString()})" }
    }

    companion object : KLogging() {
        private const val OUTBOX_COLLECTION = "outbox"
        private val REMOVED_SYNC_KEYS = listOf(
            "SyncMissingArtists",
            "SyncMissingTracks",
            "SyncMissingAlbums",
            "SyncTrackDetails",
            "EnrichTrackDetails",
        )
    }
}
