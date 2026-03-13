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
        addToPool("ARTIST", artistIds)
    }

    override fun addTracks(trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        addToPool("TRACK", trackIds)
    }

    override fun popArtists(max: Int): List<String> = popFromPool("ARTIST", max)

    override fun popTracks(max: Int): List<String> = popFromPool("TRACK", max)

    private fun addToPool(type: String, ids: List<String>) {
        val collection = appSyncPoolDocumentRepository.mongoCollection()
        val upsertOptions = UpdateOptions().upsert(true)
        mongoQueryMetrics.timed("app_sync_pool.add.$type") {
            ids.forEach { id ->
                collection.updateOne(
                    Filters.eq("_id", "$type:$id"),
                    Updates.combine(
                        Updates.setOnInsert("type", type),
                        Updates.setOnInsert("spotifyId", id),
                    ),
                    upsertOptions,
                )
            }
        }
    }

    private fun popFromPool(type: String, max: Int): List<String> {
        val collection = appSyncPoolDocumentRepository.mongoCollection()
        return mongoQueryMetrics.timed("app_sync_pool.pop.$type") {
            val docs = collection
                .find(Filters.eq("type", type))
                .limit(max)
                .toList()
            if (docs.isNotEmpty()) {
                val ids = docs.map { it.id }
                collection.deleteMany(Filters.`in`("_id", ids))
                logger.debug { "Popped ${docs.size} $type IDs from sync pool" }
            }
            docs.map { it.spotifyId }
        }
    }

    companion object : KLogging()
}
