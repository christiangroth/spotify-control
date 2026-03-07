package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import de.chrgroth.spotify.control.domain.model.AppAlbum
import de.chrgroth.spotify.control.domain.port.out.AppAlbumRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
class AppAlbumRepositoryAdapter : AppAlbumRepositoryPort {

    @Inject
    lateinit var appAlbumDocumentRepository: AppAlbumDocumentRepository

    @Inject
    lateinit var mongoQueryMetrics: MongoQueryMetrics

    override fun upsertAll(items: List<AppAlbum>) {
        if (items.isEmpty()) return
        val collection = appAlbumDocumentRepository.mongoCollection()
        val upsertOptions = UpdateOptions().upsert(true)
        mongoQueryMetrics.timed("app_album.upsertAll") {
            items.forEach { item ->
                collection.updateOne(
                    Filters.eq("_id", item.albumId),
                    Updates.combine(
                        Updates.setOnInsert("albumTitle", item.albumTitle),
                        Updates.setOnInsert("imageLink", item.imageLink),
                        Updates.setOnInsert("genres", item.genres),
                        Updates.setOnInsert("artistId", item.artistId),
                    ),
                    upsertOptions,
                )
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

    override fun updateEnrichmentData(albumId: String, albumTitle: String?, imageLink: String?, genres: List<String>, artistId: String?) {
        val now = java.time.Instant.now()
        mongoQueryMetrics.timed("app_album.updateEnrichmentData") {
            appAlbumDocumentRepository.mongoCollection().updateOne(
                Filters.eq("_id", albumId),
                Updates.combine(
                    Updates.set("albumTitle", albumTitle),
                    Updates.set("imageLink", imageLink),
                    Updates.set("genres", genres),
                    Updates.set("artistId", artistId),
                    Updates.set("lastEnrichmentDate", now),
                ),
            )
        }
    }

    private fun AppAlbumDocument.toDomain() = AppAlbum(
        albumId = id,
        albumTitle = albumTitle,
        imageLink = imageLink,
        genres = genres,
        artistId = artistId,
        lastEnrichmentDate = lastEnrichmentDate?.toKotlinInstant(),
    )

    companion object : KLogging()
}
