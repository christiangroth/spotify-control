package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.RecentlyPlayedPort
import de.chrgroth.spotify.control.domain.port.out.CurrentlyPlayingRepositoryPort
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
    private val currentlyPlayingRepository: CurrentlyPlayingRepositoryPort,
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
        val after = recentlyPlayedRepository.findMostRecentPlayedAt(userId)
        return spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, after).flatMap { tracks ->
            val playedAts = tracks.map { it.playedAt }.toSet()
            val existingPlayedAts = recentlyPlayedRepository.findExistingPlayedAts(userId, playedAts)
            val newItems = tracks.filter { it.playedAt !in existingPlayedAts }
            if (newItems.isNotEmpty()) {
                logger.info { "Persisting ${newItems.size} new recently played items for user: ${userId.value}" }
                recentlyPlayedRepository.saveAll(newItems)
            }
            val computedCount = convertPartialPlays(userId, tracks.map { it.trackId }.toSet())
            if (newItems.isNotEmpty() || computedCount > 0) {
                dashboardRefresh.notifyUserPlaybackData(userId)
            }
            Unit.right()
        }
    }

    private fun convertPartialPlays(userId: UserId, completedTrackIds: Set<String>): Int {
        currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, completedTrackIds)
        val remaining = currentlyPlayingRepository.findByUserId(userId)
        if (remaining.isEmpty()) return 0

        val latestTrackId = remaining.maxByOrNull { it.observedAt }?.trackId
        // Exclude the most recently observed track (may still be playing) and tracks played less than 25s
        val convertible = remaining
            .groupBy { it.trackId }
            .filter { (trackId, items) -> trackId != latestTrackId && items.maxOf { it.progressMs } > MINIMUM_PROGRESS_MS }
        if (convertible.isEmpty()) return 0

        val computedItems = convertible.map { (_, items) ->
            val representative = items.maxBy { it.progressMs }
            RecentlyPlayedItem(
                spotifyUserId = userId,
                trackId = representative.trackId,
                trackName = representative.trackName,
                artistIds = representative.artistIds,
                artistNames = representative.artistNames,
                playedAt = items.maxOf { it.observedAt },
            )
        }
        val existingPlayedAts = recentlyPlayedRepository.findExistingPlayedAts(userId, computedItems.map { it.playedAt }.toSet())
        val newComputed = computedItems.filter { it.playedAt !in existingPlayedAts }
        if (newComputed.isNotEmpty()) {
            logger.info { "Persisting ${newComputed.size} computed recently played items from partial plays for user: ${userId.value}" }
            recentlyPlayedRepository.saveAll(newComputed)
        }
        currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, convertible.keys)
        return newComputed.size
    }

    companion object : KLogging() {
        private const val MINIMUM_PROGRESS_MS = 25_000L
    }
}
