package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.CurrentlyPlayingPort
import de.chrgroth.spotify.control.domain.port.out.CurrentlyPlayingRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.PlaybackStatePort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyCurrentlyPlayingPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import de.chrgroth.spotify.control.domain.error.DomainError
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class CurrentlyPlayingAdapter(
    private val userRepository: UserRepositoryPort,
    private val spotifyAccessToken: SpotifyAccessTokenPort,
    private val spotifyCurrentlyPlaying: SpotifyCurrentlyPlayingPort,
    private val currentlyPlayingRepository: CurrentlyPlayingRepositoryPort,
    private val outboxPort: OutboxPort,
    private val dashboardRefresh: DashboardRefreshPort,
    private val playbackState: PlaybackStatePort,
) : CurrentlyPlayingPort {

    override fun enqueueUpdates() {
        val users = userRepository.findAll()
        logger.info { "Scheduling currently playing fetch for ${users.size} user(s)" }
        users.forEach { user ->
            outboxPort.enqueue(DomainOutboxEvent.FetchCurrentlyPlaying(user.spotifyUserId))
        }
    }

    override fun update(userId: UserId): Either<DomainError, Unit> {
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyCurrentlyPlaying.getCurrentlyPlaying(userId, accessToken).flatMap { item ->
            if (item != null && item.isPlaying) {
                playbackState.onPlaybackDetected()
            }
            if (item != null && !currentlyPlayingRepository.existsByUserAndTrackAndObservedMinute(item)) {
                logger.info { "Persisting currently playing item for user: ${userId.value}, track: ${item.trackId}" }
                currentlyPlayingRepository.save(item)
                dashboardRefresh.notifyUserPlaybackData(userId)
            }
            Unit.right()
        }
    }

    companion object : KLogging()
}
