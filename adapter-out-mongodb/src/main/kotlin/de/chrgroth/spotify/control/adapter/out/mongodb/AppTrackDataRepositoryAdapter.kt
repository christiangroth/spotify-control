package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
class AppTrackRepositoryAdapter : AppTrackRepositoryPort {

    @Inject
    lateinit var appTrackDocumentRepository: AppTrackDocumentRepository

    @Inject
    lateinit var mongoQueryMetrics: MongoQueryMetrics

    override fun upsertAll(items: List<AppTrack>) {
        if (items.isEmpty()) return
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
            appTrackDocumentRepository.mongoCollection()
                .find(Filters.`in`("_id", trackIds))
                .toList()
                .map { it.toDomain() }
        }
    }

    override fun findByArtistId(artistId: String): List<AppTrack> =
        mongoQueryMetrics.timed("app_track.findByArtistId") {
            appTrackDocumentRepository.list("artistId = ?1", artistId).map { it.toDomain() }
        }

    override fun updateAlbumId(trackId: String, albumId: String) {
        val now = java.time.Instant.now()
        mongoQueryMetrics.timed("app_track.updateAlbumId") {
            appTrackDocumentRepository.mongoCollection().updateOne(
                Filters.eq("_id", trackId),
                Updates.combine(
                    Updates.set("albumId", albumId),
                    Updates.set("lastEnrichmentDate", now),
                ),
            )
        }
    }

    private fun AppTrackDocument.toDomain() = AppTrack(
        trackId = id,
        trackTitle = trackTitle,
        albumId = albumId,
        artistId = artistId,
        additionalArtistIds = additionalArtistIds,
        lastEnrichmentDate = lastEnrichmentDate?.toKotlinInstant(),
    )

    companion object : KLogging()
}
