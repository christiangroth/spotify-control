package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import de.chrgroth.quarkus.starters.Starter
import de.chrgroth.spotify.control.domain.port.out.AppSyncPoolRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class DeletePendingSyncMissingTasksStarter(
    private val mongoClient: MongoClient,
    @param:ConfigProperty(name = "quarkus.mongodb.database")
    private val databaseName: String,
    private val syncPoolRepository: AppSyncPoolRepositoryPort,
) : Starter {

    override val id = "DeletePendingSyncMissingTasksStarter-v1"

    override fun execute() {
        val outboxResult = mongoClient.getDatabase(databaseName)
            .getCollection(OUTBOX_COLLECTION)
            .deleteMany(Filters.`in`("eventType", SYNC_MISSING_KEYS))
        logger.info { "Deleted ${outboxResult.deletedCount} pending sync-missing outbox tasks (${SYNC_MISSING_KEYS.joinToString()})" }

        syncPoolRepository.resetEnqueued()
        logger.info { "Reset enqueued flag for all sync pool items" }
    }

    companion object : KLogging() {
        private const val OUTBOX_COLLECTION = "outbox"
        private val SYNC_MISSING_KEYS = listOf(
            "SyncMissingArtists",
            "SyncMissingTracks",
            "SyncMissingAlbums",
        )
    }
}
