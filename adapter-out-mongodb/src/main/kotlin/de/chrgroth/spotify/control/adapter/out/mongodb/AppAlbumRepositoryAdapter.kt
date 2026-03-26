package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.BulkWriteOptions
import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOneModel
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AppAlbum
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
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
            val requests = items.map { item ->
                UpdateOneModel<AppAlbumDocument>(
                    Filters.eq("_id", item.id.value),
                    Updates.combine(
                        Updates.set("totalTracks", item.totalTracks),
                        Updates.set("title", item.title),
                        Updates.set("imageLink", item.imageLink),
                        Updates.set("releaseDate", item.releaseDate),
                        Updates.set("releaseDatePrecision", item.releaseDatePrecision),
                        Updates.set("type", item.type),
                        Updates.set("artistId", item.artistId?.value),
                        Updates.set("artistName", item.artistName),
                        Updates.set("additionalArtistIds", item.additionalArtistIds?.map { it.value }),
                        Updates.set("additionalArtistNames", item.additionalArtistNames),
                        Updates.set("lastSync", now),
                    ),
                    upsertOptions,
                )
            }
            collection.bulkWrite(requests, BulkWriteOptions().ordered(false))
        }
    }

    override fun findAll(): List<AppAlbum> =
        mongoQueryMetrics.timed("app_album.findAll") {
            appAlbumDocumentRepository.listAll().map { it.toDomain() }
        }

    override fun countAll(): Long =
        mongoQueryMetrics.timed("app_album.countAll") {
            appAlbumDocumentRepository.count()
        }

    override fun findByAlbumIds(albumIds: Set<AlbumId>): List<AppAlbum> {
        if (albumIds.isEmpty()) return emptyList()
        return mongoQueryMetrics.timed("app_album.findByAlbumIds") {
            appAlbumDocumentRepository.mongoCollection()
                .find(Filters.`in`("_id", albumIds.map { it.value }))
                .toList()
                .map { it.toDomain() }
        }
    }

    override fun findByArtistId(artistId: ArtistId): List<AppAlbum> =
        mongoQueryMetrics.timed("app_album.findByArtistId") {
            appAlbumDocumentRepository.list("artistId = ?1", artistId.value).map { it.toDomain() }
        }

    override fun deleteAll() {
        logger.info { "Deleting all app_album documents" }
        mongoQueryMetrics.timed("app_album.deleteAll") {
            appAlbumDocumentRepository.deleteAll()
        }
    }

    private fun AppAlbumDocument.toDomain() = AppAlbum(
        id = AlbumId(id),
        totalTracks = totalTracks,
        title = title,
        imageLink = imageLink,
        releaseDate = releaseDate,
        releaseDatePrecision = releaseDatePrecision,
        type = type,
        artistId = artistId?.let { ArtistId(it) },
        artistName = artistName,
        additionalArtistIds = additionalArtistIds?.map { ArtistId(it) },
        additionalArtistNames = additionalArtistNames,
        lastSync = lastSync?.toKotlinInstant() ?: kotlin.time.Instant.DISTANT_PAST,
    )

    companion object : KLogging()
}
