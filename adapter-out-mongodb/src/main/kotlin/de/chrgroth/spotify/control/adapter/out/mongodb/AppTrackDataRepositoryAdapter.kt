package de.chrgroth.spotify.control.adapter.out.mongodb

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
        items.forEach { item ->
            val document = item.toDocument()
            mongoQueryMetrics.timed("app_track.upsertAll") {
                appTrackDocumentRepository.persistOrUpdate(document)
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

    private fun AppTrackDocument.toDomain() = AppTrack(
        trackId = id,
        trackTitle = trackTitle,
        albumId = albumId,
        artistId = artistId,
        additionalArtistIds = additionalArtistIds,
    )

    private fun AppTrack.toDocument() = AppTrackDocument().apply {
        id = this@toDocument.trackId
        trackTitle = this@toDocument.trackTitle
        albumId = this@toDocument.albumId
        artistId = this@toDocument.artistId
        additionalArtistIds = this@toDocument.additionalArtistIds
    }

    companion object : KLogging()
}
