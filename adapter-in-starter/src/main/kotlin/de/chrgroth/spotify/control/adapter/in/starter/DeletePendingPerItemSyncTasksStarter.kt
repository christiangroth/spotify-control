package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class DeletePendingPerItemSyncTasksStarter(
    private val mongoClient: MongoClient,
    @param:ConfigProperty(name = "quarkus.mongodb.database")
    private val databaseName: String,
) : Starter {

    override val id = "DeletePendingPerItemSyncTasksStarter-v1"

    override fun execute() {
        val result = mongoClient.getDatabase(databaseName)
            .getCollection(OUTBOX_COLLECTION)
            .deleteMany(Filters.`in`("eventType", PER_ITEM_SYNC_KEYS))
        logger.info { "Deleted ${result.deletedCount} pending per-item sync outbox tasks (${PER_ITEM_SYNC_KEYS.joinToString()})" }
    }

    companion object : KLogging() {
        private const val OUTBOX_COLLECTION = "outbox"
        private val PER_ITEM_SYNC_KEYS = listOf(
            "EnrichArtistDetails",
            "EnrichTrackDetails",
            "SyncArtistDetails",
            "SyncTrackDetails",
        )
    }
}
