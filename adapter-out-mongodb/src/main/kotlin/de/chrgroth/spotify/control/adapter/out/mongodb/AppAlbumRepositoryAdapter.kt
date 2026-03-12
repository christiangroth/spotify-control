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
        val now = java.time.Instant.now()
        mongoQueryMetrics.timed("app_album.upsertAll") {
            items.forEach { item ->
                collection.updateOne(
                    Filters.eq("_id", item.albumId),
                    Updates.combine(
                        Updates.set("totalTracks", item.totalTracks),
                        Updates.set("albumTitle", item.albumTitle),
                        Updates.set("imageLink", item.imageLink),
                        Updates.set("releaseDate", item.releaseDate),
                        Updates.set("releaseDatePrecision", item.releaseDatePrecision),
                        Updates.set("type", item.type),
                        Updates.set("artistId", item.artistId),
                        Updates.set("artistName", item.artistName),
                        Updates.set("additionalArtistIds", item.additionalArtistIds),
                        Updates.set("additionalArtistNames", item.additionalArtistNames),
                        Updates.set("lastEnrichmentDate", now),
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

    private fun AppAlbumDocument.toDomain() = AppAlbum(
        albumId = id,
        totalTracks = totalTracks,
        albumTitle = albumTitle,
        imageLink = imageLink,
        releaseDate = releaseDate,
        releaseDatePrecision = releaseDatePrecision,
        type = type,
        artistId = artistId,
        artistName = artistName,
        additionalArtistIds = additionalArtistIds,
        additionalArtistNames = additionalArtistNames,
        genreOverrides = genreOverrides,
        lastEnrichmentDate = lastEnrichmentDate?.toKotlinInstant(),
    )

    companion object : KLogging()
}
