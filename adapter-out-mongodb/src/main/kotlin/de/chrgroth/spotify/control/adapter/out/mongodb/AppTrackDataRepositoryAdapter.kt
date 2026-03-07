package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.AppTrackData
import de.chrgroth.spotify.control.domain.port.out.AppTrackDataRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import mu.KLogging

@ApplicationScoped
class AppTrackDataRepositoryAdapter : AppTrackDataRepositoryPort {

    @Inject
    lateinit var appTrackDataDocumentRepository: AppTrackDataDocumentRepository

    @Inject
    lateinit var mongoQueryMetrics: MongoQueryMetrics

    override fun upsertAll(items: List<AppTrackData>) {
        if (items.isEmpty()) return
        logger.info { "Upserting ${items.size} app_track_data documents" }
        items.forEach { item ->
            val document = item.toDocument()
            mongoQueryMetrics.timed("app_track_data.upsertAll") {
                appTrackDataDocumentRepository.persistOrUpdate(document)
            }
        }
    }

    override fun findByTrackIds(trackIds: Set<String>): List<AppTrackData> {
        if (trackIds.isEmpty()) return emptyList()
        return mongoQueryMetrics.timed("app_track_data.findByTrackIds") {
            trackIds.mapNotNull { trackId ->
                appTrackDataDocumentRepository.findById(trackId)?.toDomain()
            }
        }
    }

    private fun AppTrackDataDocument.toDomain() = AppTrackData(
        trackId = id,
        albumId = albumId,
        artistIds = artistIds,
        trackTitle = trackTitle,
        albumTitle = albumTitle,
        artistNames = artistNames,
        genres = genres,
        imageLink = imageLink,
    )

    private fun AppTrackData.toDocument() = AppTrackDataDocument().apply {
        id = this@toDocument.trackId
        albumId = this@toDocument.albumId
        artistIds = this@toDocument.artistIds
        trackTitle = this@toDocument.trackTitle
        albumTitle = this@toDocument.albumTitle
        artistNames = this@toDocument.artistNames
        genres = this@toDocument.genres
        imageLink = this@toDocument.imageLink
    }

    companion object : KLogging()
}
