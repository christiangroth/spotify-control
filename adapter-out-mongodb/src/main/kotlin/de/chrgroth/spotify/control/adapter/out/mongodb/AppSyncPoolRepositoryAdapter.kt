package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import de.chrgroth.spotify.control.domain.port.out.AppSyncPoolRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import mu.KLogging

@ApplicationScoped
class AppSyncPoolRepositoryAdapter : AppSyncPoolRepositoryPort {

    @Inject
    lateinit var appSyncPoolDocumentRepository: AppSyncPoolDocumentRepository

    @Inject
    lateinit var mongoQueryMetrics: MongoQueryMetrics

    override fun addArtists(artistIds: List<String>) {
        if (artistIds.isEmpty()) return
        addToPool(SyncPoolType.ARTIST, artistIds)
    }

    override fun addTracks(trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        addToPool(SyncPoolType.TRACK, trackIds)
    }

    override fun peekArtists(max: Int): List<String> = peekFromPool(SyncPoolType.ARTIST, max)

    override fun peekTracks(max: Int): List<String> = peekFromPool(SyncPoolType.TRACK, max)

    override fun removeArtists(artistIds: List<String>) {
        if (artistIds.isEmpty()) return
        removeFromPool(SyncPoolType.ARTIST, artistIds)
    }

    override fun removeTracks(trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        removeFromPool(SyncPoolType.TRACK, trackIds)
    }

    private fun addToPool(type: SyncPoolType, ids: List<String>) {
        val collection = appSyncPoolDocumentRepository.mongoCollection()
        val upsertOptions = UpdateOptions().upsert(true)
        mongoQueryMetrics.timed("app_sync_pool.add.${type.name}") {
            ids.forEach { id ->
                collection.updateOne(
                    Filters.eq("_id", "${type.name}:$id"),
                    Updates.combine(
                        Updates.setOnInsert("type", type.name),
                        Updates.setOnInsert("spotifyId", id),
                    ),
                    upsertOptions,
                )
            }
        }
    }

    private fun peekFromPool(type: SyncPoolType, max: Int): List<String> {
        val collection = appSyncPoolDocumentRepository.mongoCollection()
        return mongoQueryMetrics.timed("app_sync_pool.peek.${type.name}") {
            collection
                .find(Filters.eq("type", type.name))
                .limit(max)
                .map { it.spotifyId }
                .toList()
        }
    }

    private fun removeFromPool(type: SyncPoolType, ids: List<String>) {
        val collection = appSyncPoolDocumentRepository.mongoCollection()
        val documentIds = ids.map { "${type.name}:$it" }
        mongoQueryMetrics.timed("app_sync_pool.remove.${type.name}") {
            collection.deleteMany(Filters.`in`("_id", documentIds))
            logger.debug { "Removed ${ids.size} ${type.name} IDs from sync pool" }
        }
    }

    companion object : KLogging()
}
