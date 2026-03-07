package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import mu.KLogging

@ApplicationScoped
class AppTrackRepositoryAdapter : AppTrackRepositoryPort {

    @Inject
    lateinit var appTrackDocumentRepository: AppTrackDocumentRepository

    @Inject
    lateinit var mongoQueryMetrics: MongoQueryMetrics

    override fun upsertAll(items: List<AppTrack>) {
        if (items.isEmpty()) return
        logger.info { "Upserting ${items.size} app_track documents" }
        val collection = appTrackDocumentRepository.mongoCollection()
        val upsertOptions = UpdateOptions().upsert(true)
        mongoQueryMetrics.timed("app_track.upsertAll") {
            items.forEach { item ->
                collection.updateOne(
                    Filters.eq("_id", item.trackId),
                    Updates.combine(
                        Updates.set("trackTitle", item.trackTitle),
                        Updates.set("artistId", item.artistId),
                        Updates.set("additionalArtistIds", item.additionalArtistIds),
                        // $setOnInsert ensures albumId is only set on new documents,
                        // never overwriting albumId already populated by enrichment
                        Updates.setOnInsert("albumId", item.albumId),
                    ),
                    upsertOptions,
                )
            }
        }
    }

    override fun findByTrackIds(trackIds: Set<String>): List<AppTrack> {
        if (trackIds.isEmpty()) return emptyList()
        return mongoQueryMetrics.timed("app_track.findByTrackIds") {
            trackIds.mapNotNull { trackId ->
                appTrackDocumentRepository.findById(trackId)?.toDomain()
            }
        }
    }

    override fun updateAlbumId(trackId: String, albumId: String) {
        mongoQueryMetrics.timed("app_track.updateAlbumId") {
            appTrackDocumentRepository.mongoCollection().updateOne(
                Filters.eq("_id", trackId),
                Updates.set("albumId", albumId),
            )
        }
    }

    private fun AppTrackDocument.toDomain() = AppTrack(
        trackId = id,
        trackTitle = trackTitle,
        albumId = albumId,
        artistId = artistId,
        additionalArtistIds = additionalArtistIds,
    )

    companion object : KLogging()
}
