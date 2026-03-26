package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.playback.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.playback.CurrentlyPlayingRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
class CurrentlyPlayingRepositoryAdapter : CurrentlyPlayingRepositoryPort {

    @Inject
    lateinit var currentlyPlayingDocumentRepository: SpotifyCurrentlyPlayingDocumentRepository

    @Inject
    lateinit var mongoQueryMetrics: MongoQueryMetrics

    override fun save(item: CurrentlyPlayingItem) {
        val document = SpotifyCurrentlyPlayingDocument().apply {
            spotifyUserId = item.spotifyUserId.value
            trackId = item.trackId.value
            trackName = item.trackName
            artistIds = item.artistIds.map { it.value }
            artistNames = item.artistNames
            progressMs = item.progressMs
            durationMs = item.durationMs
            isPlaying = item.isPlaying
            observedAt = item.observedAt.toJavaInstant()
            albumId = item.albumId?.value
        }
        logger.info { "Saving currently playing document for user ${item.spotifyUserId.value}, track ${item.trackId.value}" }
        mongoQueryMetrics.timed("spotify_currently_playing.save") {
            currentlyPlayingDocumentRepository.persist(document)
        }
    }

    override fun existsByUserAndTrackAndObservedMinute(item: CurrentlyPlayingItem): Boolean {
        val observedMinuteStart = item.observedAt.toJavaInstant().truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
        val observedMinuteEnd = observedMinuteStart.plusSeconds(SECONDS_PER_MINUTE)
        return mongoQueryMetrics.timed("spotify_currently_playing.existsByUserAndTrackAndObservedMinute") {
            currentlyPlayingDocumentRepository.count(
                "spotifyUserId = ?1 and trackId = ?2 and observedAt >= ?3 and observedAt < ?4",
                item.spotifyUserId.value,
                item.trackId.value,
                observedMinuteStart,
                observedMinuteEnd,
            ) > 0
        }
    }

    override fun updateProgressByUserAndTrackAndObservedMinute(item: CurrentlyPlayingItem) {
        val observedMinuteStart = item.observedAt.toJavaInstant().truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
        val observedMinuteEnd = observedMinuteStart.plusSeconds(SECONDS_PER_MINUTE)
        logger.info { "Updating currently playing progress for user ${item.spotifyUserId.value}, track ${item.trackId.value}, progressMs=${item.progressMs}" }
        mongoQueryMetrics.timed("spotify_currently_playing.updateProgressByUserAndTrackAndObservedMinute") {
            currentlyPlayingDocumentRepository.mongoCollection().updateOne(
                Filters.and(
                    Filters.eq("spotifyUserId", item.spotifyUserId.value),
                    Filters.eq("trackId", item.trackId.value),
                    Filters.gte("observedAt", observedMinuteStart),
                    Filters.lt("observedAt", observedMinuteEnd),
                ),
                Updates.combine(
                    Updates.set("progressMs", item.progressMs),
                    Updates.set("isPlaying", item.isPlaying),
                    Updates.set("observedAt", item.observedAt.toJavaInstant()),
                ),
            )
        }
    }

    override fun findByUserId(userId: UserId): List<CurrentlyPlayingItem> =
        mongoQueryMetrics.timed("spotify_currently_playing.findByUserId") {
            currentlyPlayingDocumentRepository
                .list("spotifyUserId = ?1", userId.value)
                .map { doc ->
                    CurrentlyPlayingItem(
                        spotifyUserId = UserId(doc.spotifyUserId),
                        trackId = TrackId(doc.trackId),
                        trackName = doc.trackName,
                        artistIds = doc.artistIds.map { ArtistId(it) },
                        artistNames = doc.artistNames,
                        progressMs = doc.progressMs,
                        durationMs = doc.durationMs,
                        isPlaying = doc.isPlaying,
                        observedAt = doc.observedAt.toKotlinInstant(),
                        albumId = doc.albumId?.let { AlbumId(it) },
                    )
                }
        }

    override fun deleteByUserIdAndTrackIds(userId: UserId, trackIds: Set<String>) {
        if (trackIds.isEmpty()) return
        mongoQueryMetrics.timed("spotify_currently_playing.deleteByUserIdAndTrackIds") {
            currentlyPlayingDocumentRepository.delete(
                "spotifyUserId = ?1 and trackId in ?2",
                userId.value,
                trackIds.toList(),
            )
        }
    }

    companion object : KLogging() {
        private const val SECONDS_PER_MINUTE = 60L
    }
}
