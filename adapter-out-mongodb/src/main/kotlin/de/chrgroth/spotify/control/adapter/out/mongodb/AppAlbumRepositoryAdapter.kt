package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.AppAlbum
import de.chrgroth.spotify.control.domain.port.out.AppAlbumRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import mu.KLogging

@ApplicationScoped
class AppAlbumRepositoryAdapter : AppAlbumRepositoryPort {

    @Inject
    lateinit var appAlbumDocumentRepository: AppAlbumDocumentRepository

    @Inject
    lateinit var mongoQueryMetrics: MongoQueryMetrics

    override fun upsertAll(items: List<AppAlbum>) {
        if (items.isEmpty()) return
        logger.info { "Upserting ${items.size} app_album documents" }
        items.forEach { item ->
            val document = item.toDocument()
            mongoQueryMetrics.timed("app_album.upsertAll") {
                appAlbumDocumentRepository.persistOrUpdate(document)
            }
        }
    }

    override fun findByAlbumIds(albumIds: Set<String>): List<AppAlbum> {
        if (albumIds.isEmpty()) return emptyList()
        return mongoQueryMetrics.timed("app_album.findByAlbumIds") {
            albumIds.mapNotNull { albumId ->
                appAlbumDocumentRepository.findById(albumId)?.toDomain()
            }
        }
    }

    private fun AppAlbumDocument.toDomain() = AppAlbum(
        albumId = id,
        albumTitle = albumTitle,
        imageLink = imageLink,
    )

    private fun AppAlbum.toDocument() = AppAlbumDocument().apply {
        id = this@toDocument.albumId
        albumTitle = this@toDocument.albumTitle
        imageLink = this@toDocument.imageLink
    }

    companion object : KLogging()
}
