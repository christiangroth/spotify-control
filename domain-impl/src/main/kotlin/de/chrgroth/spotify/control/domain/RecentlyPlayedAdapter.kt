package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItemComputed
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.RecentlyPlayedPort
import de.chrgroth.spotify.control.domain.port.out.ComputedRecentlyPlayedRepositoryPort
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
    private val computedRecentlyPlayedRepository: ComputedRecentlyPlayedRepositoryPort,
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
        val allItems = currentlyPlayingRepository.findByUserId(userId)
        val sortedItems = allItems.sortedBy { it.observedAt }
        val latestNonCompletedTrackId = allItems
            .filter { it.trackId !in completedTrackIds }
            .maxByOrNull { it.observedAt }?.trackId
        // Exclude completed tracks, the most recently observed non-completed track (may still be active),
        // and tracks played less than 25s
        val convertible = allItems
            .groupBy { it.trackId }
            .filter { (trackId, items) ->
                trackId !in completedTrackIds
                    && trackId != latestNonCompletedTrackId
                    && items.maxOf { it.progressMs } > MINIMUM_PROGRESS_MS
            }
        val newComputedCount = if (convertible.isNotEmpty()) {
            val computedItems = convertible.map { (trackId, items) ->
                val representative = items.maxBy { it.progressMs }
                val firstObservedAt = items.minOf { it.observedAt }
                val lastObservedAtForTrack = items.maxOf { it.observedAt }
                val nextItem = sortedItems.firstOrNull { it.observedAt > lastObservedAtForTrack && it.trackId != trackId }
                val playedMs = if (nextItem != null) {
                    (nextItem.observedAt - firstObservedAt).inWholeMilliseconds
                } else {
                    items.maxOf { it.progressMs }
                }
                RecentlyPlayedItemComputed(
                    spotifyUserId = userId,
                    trackId = trackId,
                    trackName = representative.trackName,
                    artistIds = representative.artistIds,
                    artistNames = representative.artistNames,
                    playedAt = firstObservedAt,
                    playedMs = playedMs,
                )
            }
            val existingPlayedAts = computedRecentlyPlayedRepository.findExistingPlayedAts(userId, computedItems.map { it.playedAt }.toSet())
            val newComputed = computedItems.filter { it.playedAt !in existingPlayedAts }
            if (newComputed.isNotEmpty()) {
                logger.info { "Persisting ${newComputed.size} computed recently played items from partial plays for user: ${userId.value}" }
                computedRecentlyPlayedRepository.saveAll(newComputed)
            }
            newComputed.size
        } else {
            0
        }
        currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, completedTrackIds + convertible.keys)
        return newComputedCount
    }

    companion object : KLogging() {
        private const val MINIMUM_PROGRESS_MS = 25_000L
    }
}
