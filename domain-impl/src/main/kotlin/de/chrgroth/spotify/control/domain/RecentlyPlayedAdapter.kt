package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import de.chrgroth.spotify.control.domain.model.RecentlyPartialPlayedItem
import de.chrgroth.spotify.control.domain.model.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.RecentlyPlayedPort
import de.chrgroth.spotify.control.domain.port.out.RecentlyPartialPlayedRepositoryPort
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
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class RecentlyPlayedAdapter(
    private val userRepository: UserRepositoryPort,
    private val spotifyAccessToken: SpotifyAccessTokenPort,
    private val spotifyRecentlyPlayed: SpotifyRecentlyPlayedPort,
    private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort,
    private val currentlyPlayingRepository: CurrentlyPlayingRepositoryPort,
    private val recentlyPartialPlayedRepository: RecentlyPartialPlayedRepositoryPort,
    private val outboxPort: OutboxPort,
    private val dashboardRefresh: DashboardRefreshPort,
    @ConfigProperty(name = "app.playback.minimum-progress-seconds", defaultValue = "25")
    minimumProgressSeconds: Long,
) : RecentlyPlayedPort {

    private val minimumProgressMs = minimumProgressSeconds * MS_PER_SECOND

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
                outboxPort.enqueue(DomainOutboxEvent.AppendPlaybackData(userId))
            }
            Unit.right()
        }
    }

    private fun convertPartialPlays(userId: UserId, completedTrackIds: Set<String>): Int {
        val allItems = currentlyPlayingRepository.findByUserId(userId)
        val sortedItems = allItems.sortedBy { it.observedAt }

        // Group items into contiguous play sessions per track.
        // A new session for a track begins whenever a different track is observed between observations of the same track.
        val sessions = buildSessions(sortedItems)

        // The most recently observed non-completed session is protected — it may still be active
        val latestNonCompletedSession = sessions
            .filter { it.trackId !in completedTrackIds }
            .maxByOrNull { session -> session.items.maxOf { it.observedAt } }

        val convertibleSessions = sessions.filter { session ->
            session.trackId !in completedTrackIds
                && session !== latestNonCompletedSession
                && session.items.maxOf { it.progressMs } > minimumProgressMs
        }

        val newComputedCount = if (convertibleSessions.isNotEmpty()) {
            val partialItems = convertibleSessions.map { session ->
                val firstObservedAt = session.items.minOf { it.observedAt }
                val lastObservedAtForSession = session.items.maxOf { it.observedAt }
                val nextItem = sortedItems.firstOrNull { it.observedAt > lastObservedAtForSession && it.trackId != session.trackId }
                val playedMs = if (nextItem != null) {
                    (nextItem.observedAt - firstObservedAt).inWholeMilliseconds
                } else {
                    session.items.maxOf { it.progressMs }
                }
                val representative = session.items.maxBy { it.progressMs }
                RecentlyPartialPlayedItem(
                    spotifyUserId = userId,
                    trackId = session.trackId,
                    trackName = representative.trackName,
                    artistIds = representative.artistIds,
                    artistNames = representative.artistNames,
                    playedAt = firstObservedAt,
                    playedSeconds = playedMs / MS_PER_SECOND,
                )
            }
            val existingPlayedAts = recentlyPartialPlayedRepository.findExistingPlayedAts(userId, partialItems.map { it.playedAt }.toSet())
            val newPartial = partialItems.filter { it.playedAt !in existingPlayedAts }
            if (newPartial.isNotEmpty()) {
                logger.info { "Persisting ${newPartial.size} recently partial played items for user: ${userId.value}" }
                recentlyPartialPlayedRepository.saveAll(newPartial)
            }
            newPartial.size
        } else {
            0
        }

        // Only delete track entries that are not held by a protected session
        val protectedTrackIds = latestNonCompletedSession?.let { setOf(it.trackId) } ?: emptySet()
        val convertedTrackIds = convertibleSessions.map { it.trackId }.filter { it !in protectedTrackIds }.toSet()
        currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, completedTrackIds + convertedTrackIds)
        return newComputedCount
    }

    private fun buildSessions(sortedItems: List<CurrentlyPlayingItem>): List<PlaySession> {
        val result = mutableListOf<PlaySession>()
        var lastTrackId: String? = null
        for (item in sortedItems) {
            if (lastTrackId == item.trackId) {
                result.last().items.add(item)
            } else {
                result.add(PlaySession(item.trackId, mutableListOf(item)))
            }
            lastTrackId = item.trackId
        }
        return result
    }

    private data class PlaySession(val trackId: String, val items: MutableList<CurrentlyPlayingItem>)

    companion object : KLogging() {
        private const val MS_PER_SECOND = 1_000L
    }
}
