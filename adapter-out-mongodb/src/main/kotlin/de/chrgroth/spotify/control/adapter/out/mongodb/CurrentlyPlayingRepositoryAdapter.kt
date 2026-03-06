package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.port.out.CurrentlyPlayingRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlin.time.toJavaInstant
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
        mongoQueryMetrics.timed("currently_playing.save") {
            currentlyPlayingDocumentRepository.persist(document)
        }
    }

    override fun existsByUserAndTrackAndObservedMinute(item: CurrentlyPlayingItem): Boolean {
        val observedMinuteStart = item.observedAt.toJavaInstant().truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
        val observedMinuteEnd = observedMinuteStart.plusSeconds(SECONDS_PER_MINUTE)
        return mongoQueryMetrics.timed("currently_playing.existsByUserAndTrackAndObservedMinute") {
            currentlyPlayingDocumentRepository.count(
                "spotifyUserId = ?1 and trackId = ?2 and observedAt >= ?3 and observedAt < ?4",
                item.spotifyUserId.value,
                item.trackId,
                observedMinuteStart,
                observedMinuteEnd,
            ) > 0
        }
    }

    companion object : KLogging() {
        private const val SECONDS_PER_MINUTE = 60L
    }
}
