package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import de.chrgroth.spotify.control.domain.model.AlbumId
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.ArtistId
import de.chrgroth.spotify.control.domain.model.TrackId
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
                    Filters.eq("_id", item.id.value),
                    Updates.combine(
                        Updates.set("title", item.title),
                        Updates.set("artistId", item.artistId.value),
                        Updates.set("additionalArtistIds", item.additionalArtistIds.map { it.value }),
                        Updates.setOnInsert("albumId", item.albumId?.value),
                    ),
                    upsertOptions,
                )
            }
        }
    }

    override fun findByTrackIds(trackIds: Set<TrackId>): List<AppTrack> {
        if (trackIds.isEmpty()) return emptyList()
        return mongoQueryMetrics.timed("app_track.findByTrackIds") {
            appTrackDocumentRepository.mongoCollection()
                .find(Filters.`in`("_id", trackIds.map { it.value }))
                .toList()
                .map { it.toDomain() }
        }
    }

    override fun findByArtistId(artistId: ArtistId): List<AppTrack> =
        mongoQueryMetrics.timed("app_track.findByArtistId") {
            appTrackDocumentRepository.list("artistId = ?1", artistId.value).map { it.toDomain() }
        }

    override fun updateTrackSyncData(track: AppTrack) {
        val now = java.time.Instant.now()
        mongoQueryMetrics.timed("app_track.updateTrackSyncData") {
            appTrackDocumentRepository.mongoCollection().updateOne(
                Filters.eq("_id", track.id.value),
                Updates.combine(
                    Updates.set("albumId", track.albumId?.value),
                    Updates.set("albumName", track.albumName),
                    Updates.set("artistName", track.artistName),
                    Updates.set("additionalArtistIds", track.additionalArtistIds.map { it.value }),
                    Updates.set("additionalArtistNames", track.additionalArtistNames),
                    Updates.set("discNumber", track.discNumber),
                    Updates.set("durationMs", track.durationMs),
                    Updates.set("trackNumber", track.trackNumber),
                    Updates.set("type", track.type),
                    Updates.set("lastSync", now),
                ),
            )
        }
    }

    private fun AppTrackDocument.toDomain() = AppTrack(
        id = TrackId(id),
        title = title,
        albumId = albumId?.let { AlbumId(it) },
        albumName = albumName,
        artistId = ArtistId(artistId),
        artistName = artistName,
        additionalArtistIds = additionalArtistIds.map { ArtistId(it) },
        additionalArtistNames = additionalArtistNames,
        discNumber = discNumber,
        durationMs = durationMs,
        trackNumber = trackNumber,
        type = type,
        lastSync = lastSync?.toKotlinInstant(),
    )

    companion object : KLogging()
}
