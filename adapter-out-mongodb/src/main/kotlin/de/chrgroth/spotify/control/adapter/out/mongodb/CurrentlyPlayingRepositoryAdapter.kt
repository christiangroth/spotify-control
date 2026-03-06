package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.CurrentlyPlayingRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
class CurrentlyPlayingRepositoryAdapter : CurrentlyPlayingRepositoryPort {

    @Inject
    lateinit var currentlyPlayingDocumentRepository: CurrentlyPlayingDocumentRepository

    @Inject
    lateinit var mongoQueryMetrics: MongoQueryMetrics

    override fun save(item: CurrentlyPlayingItem) {
        val document = CurrentlyPlayingDocument().apply {
            spotifyUserId = item.spotifyUserId.value
            trackId = item.trackId
            trackName = item.trackName
            artistIds = item.artistIds
            artistNames = item.artistNames
            progressMs = item.progressMs
            durationMs = item.durationMs
            isPlaying = item.isPlaying
            observedAt = item.observedAt.toJavaInstant()
        }
        logger.info { "Saving currently playing document for user ${item.spotifyUserId.value}, track ${item.trackId}" }
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
                item.trackId,
                observedMinuteStart,
                observedMinuteEnd,
            ) > 0
        }
    }

    override fun findByUserId(userId: UserId): List<CurrentlyPlayingItem> =
        mongoQueryMetrics.timed("spotify_currently_playing.findByUserId") {
            currentlyPlayingDocumentRepository
                .list("spotifyUserId = ?1", userId.value)
                .map { doc ->
                    CurrentlyPlayingItem(
                        spotifyUserId = UserId(doc.spotifyUserId),
                        trackId = doc.trackId,
                        trackName = doc.trackName,
                        artistIds = doc.artistIds,
                        artistNames = doc.artistNames,
                        progressMs = doc.progressMs,
                        durationMs = doc.durationMs,
                        isPlaying = doc.isPlaying,
                        observedAt = doc.observedAt.toKotlinInstant(),
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
