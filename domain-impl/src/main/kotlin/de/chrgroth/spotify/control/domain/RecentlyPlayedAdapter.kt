package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.RecentlyPlayedPort
import de.chrgroth.spotify.control.domain.port.out.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.RecentlyPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyRecentlyPlayedPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import de.chrgroth.spotify.control.domain.error.DomainError
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class RecentlyPlayedAdapter(
    private val userRepository: UserRepositoryPort,
    private val spotifyAccessToken: SpotifyAccessTokenPort,
    private val spotifyRecentlyPlayed: SpotifyRecentlyPlayedPort,
    private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort,
    private val outboxPort: OutboxPort,
    private val dashboardRefresh: DashboardRefreshPort,
) : RecentlyPlayedPort {

    override fun enqueueUpdates() {
        val users = userRepository.findAll()
        logger.info { "Scheduling recently played fetch for ${users.size} user(s)" }
        users.forEach { user ->
            outboxPort.enqueue(DomainOutboxEvent.FetchRecentlyPlayed(user.spotifyUserId))
        }
    }

    override fun update(userId: UserId): Either<DomainError, Unit> {
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken).flatMap { tracks ->
            val playedAts = tracks.map { it.playedAt }.toSet()
            val existingPlayedAts = recentlyPlayedRepository.findExistingPlayedAts(userId, playedAts)
            val newItems = tracks.filter { it.playedAt !in existingPlayedAts }
            if (newItems.isNotEmpty()) {
                logger.info { "Persisting ${newItems.size} new recently played items for user: ${userId.value}" }
                recentlyPlayedRepository.saveAll(newItems)
                dashboardRefresh.notifyUserPlaybackData(userId)
            }
            Unit.right()
        }
    }

    companion object : KLogging()
}
