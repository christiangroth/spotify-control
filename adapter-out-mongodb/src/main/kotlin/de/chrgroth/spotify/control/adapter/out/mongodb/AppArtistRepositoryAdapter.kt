package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import mu.KLogging

@ApplicationScoped
class AppArtistRepositoryAdapter : AppArtistRepositoryPort {

    @Inject
    lateinit var appArtistDocumentRepository: AppArtistDocumentRepository

    @Inject
    lateinit var mongoQueryMetrics: MongoQueryMetrics

    override fun upsertAll(items: List<AppArtist>) {
        if (items.isEmpty()) return
        logger.info { "Upserting ${items.size} app_artist documents" }
        items.forEach { item ->
            val document = item.toDocument()
            mongoQueryMetrics.timed("app_artist.upsertAll") {
                appArtistDocumentRepository.persistOrUpdate(document)
            }
        }
    }

    override fun findByArtistIds(artistIds: Set<String>): List<AppArtist> {
        if (artistIds.isEmpty()) return emptyList()
        return mongoQueryMetrics.timed("app_artist.findByArtistIds") {
            artistIds.mapNotNull { artistId ->
                appArtistDocumentRepository.findById(artistId)?.toDomain()
            }
        }
    }

    private fun AppArtistDocument.toDomain() = AppArtist(
        artistId = id,
        artistName = artistName,
        genres = genres,
    )

    private fun AppArtist.toDocument() = AppArtistDocument().apply {
        id = this@toDocument.artistId
        artistName = this@toDocument.artistName
        genres = this@toDocument.genres
    }

    companion object : KLogging()
}
