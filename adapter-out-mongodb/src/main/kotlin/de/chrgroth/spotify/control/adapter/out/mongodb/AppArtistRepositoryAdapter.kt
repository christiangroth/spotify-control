package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
class AppArtistRepositoryAdapter : AppArtistRepositoryPort {

    @Inject
    lateinit var appArtistDocumentRepository: AppArtistDocumentRepository

    @Inject
    lateinit var mongoQueryMetrics: MongoQueryMetrics

    override fun upsertAll(items: List<AppArtist>) {
        if (items.isEmpty()) return
        val collection = appArtistDocumentRepository.mongoCollection()
        val upsertOptions = UpdateOptions().upsert(true)
        mongoQueryMetrics.timed("app_artist.upsertAll") {
            items.forEach { item ->
                collection.updateOne(
                    Filters.eq("_id", item.artistId),
                    Updates.combine(
                        Updates.set("artistName", item.artistName),
                        Updates.setOnInsert("genre", item.genre),
                        Updates.setOnInsert("additionalGenres", item.additionalGenres),
                        Updates.setOnInsert("imageLink", item.imageLink),
                        Updates.setOnInsert("type", item.type),
                        Updates.setOnInsert("playbackProcessingStatus", item.playbackProcessingStatus.name),
                    ),
                    upsertOptions,
                )
            }
        }
    }

    override fun findAll(): List<AppArtist> =
        mongoQueryMetrics.timed("app_artist.findAll") {
            appArtistDocumentRepository.listAll().map { it.toDomain() }
        }

    override fun findByPlaybackProcessingStatus(status: ArtistPlaybackProcessingStatus): List<AppArtist> =
        mongoQueryMetrics.timed("app_artist.findByPlaybackProcessingStatus") {
            appArtistDocumentRepository.list("playbackProcessingStatus = ?1", status).map { it.toDomain() }
        }

    override fun findByArtistIds(artistIds: Set<String>): List<AppArtist> {
        if (artistIds.isEmpty()) return emptyList()
        return mongoQueryMetrics.timed("app_artist.findByArtistIds") {
            appArtistDocumentRepository.mongoCollection()
                .find(Filters.`in`("_id", artistIds))
                .toList()
                .map { it.toDomain() }
        }
    }

    override fun findWithImageLinkAndBlankName(): List<AppArtist> =
        mongoQueryMetrics.timed("app_artist.findWithImageLinkAndBlankName") {
            appArtistDocumentRepository.mongoCollection()
                .find(
                    Filters.and(
                        Filters.ne("imageLink", null),
                        Filters.or(
                            Filters.eq("artistName", ""),
                            Filters.exists("artistName", false),
                        ),
                    ),
                )
                .toList()
                .map { it.toDomain() }
        }

    override fun updateSyncData(artistId: String, artistName: String, genre: String?, additionalGenres: List<String>?, imageLink: String?, type: String?) {
        val now = java.time.Instant.now()
        mongoQueryMetrics.timed("app_artist.updateSyncData") {
            appArtistDocumentRepository.mongoCollection().updateOne(
                Filters.eq("_id", artistId),
                Updates.combine(
                    Updates.set("artistName", artistName),
                    Updates.set("genre", genre),
                    Updates.set("additionalGenres", additionalGenres),
                    Updates.set("imageLink", imageLink),
                    Updates.set("type", type),
                    Updates.set("lastSync", now),
                ),
            )
        }
    }

    override fun updatePlaybackProcessingStatus(artistId: String, status: ArtistPlaybackProcessingStatus) {
        mongoQueryMetrics.timed("app_artist.updatePlaybackProcessingStatus") {
            appArtistDocumentRepository.mongoCollection().updateOne(
                Filters.eq("_id", artistId),
                Updates.set("playbackProcessingStatus", status.name),
            )
        }
    }

    private fun AppArtistDocument.toDomain() = AppArtist(
        artistId = id,
        artistName = artistName,
        genre = genre,
        additionalGenres = additionalGenres,
        imageLink = imageLink,
        type = type,
        lastSync = lastSync?.toKotlinInstant(),
        playbackProcessingStatus = playbackProcessingStatus,
    )

    companion object : KLogging()
}
