package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.BulkWriteOptions
import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOneModel
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
        val now = java.time.Instant.now()
        mongoQueryMetrics.timed("app_artist.upsertAll") {
            val requests = items.map { item ->
                UpdateOneModel<AppArtistDocument>(
                    Filters.eq("_id", item.artistId),
                    Updates.combine(
                        Updates.set("artistName", item.artistName),
                        Updates.set("imageLink", item.imageLink),
                        Updates.set("type", item.type),
                        Updates.set("lastSync", now),
                        Updates.setOnInsert("playbackProcessingStatus", item.playbackProcessingStatus.name),
                    ),
                    upsertOptions,
                )
            }
            collection.bulkWrite(requests, BulkWriteOptions().ordered(false))
        }
    }

    override fun findAll(): List<AppArtist> =
        mongoQueryMetrics.timedWithFallback("app_artist.findAll", emptyList()) {
            appArtistDocumentRepository.listAll().map { it.toDomain() }
        }

    override fun findByPlaybackProcessingStatus(status: ArtistPlaybackProcessingStatus): List<AppArtist> =
        mongoQueryMetrics.timedWithFallback("app_artist.findByPlaybackProcessingStatus", emptyList()) {
            appArtistDocumentRepository.list("playbackProcessingStatus = ?1", status).map { it.toDomain() }
        }

    override fun findByArtistIds(artistIds: Set<String>): List<AppArtist> {
        if (artistIds.isEmpty()) return emptyList()
        return mongoQueryMetrics.timedWithFallback("app_artist.findByArtistIds", emptyList()) {
            appArtistDocumentRepository.mongoCollection()
                .find(Filters.`in`("_id", artistIds))
                .toList()
                .map { it.toDomain() }
        }
    }

    override fun findWithImageLinkAndBlankName(): List<AppArtist> =
        mongoQueryMetrics.timedWithFallback("app_artist.findWithImageLinkAndBlankName", emptyList()) {
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

    override fun updatePlaybackProcessingStatus(artistId: String, status: ArtistPlaybackProcessingStatus) {
        mongoQueryMetrics.timed("app_artist.updatePlaybackProcessingStatus") {
            appArtistDocumentRepository.mongoCollection().updateOne(
                Filters.eq("_id", artistId),
                Updates.set("playbackProcessingStatus", status.name),
            )
        }
    }

    override fun deleteAll() {
        logger.info { "Deleting all app_artist documents" }
        mongoQueryMetrics.timed("app_artist.deleteAll") {
            appArtistDocumentRepository.deleteAll()
        }
    }

    private fun AppArtistDocument.toDomain() = AppArtist(
        artistId = id,
        artistName = artistName,
        imageLink = imageLink,
        type = type,
        lastSync = lastSync?.toKotlinInstant() ?: kotlin.time.Instant.DISTANT_PAST,
        playbackProcessingStatus = playbackProcessingStatus,
    )

    companion object : KLogging()
}
