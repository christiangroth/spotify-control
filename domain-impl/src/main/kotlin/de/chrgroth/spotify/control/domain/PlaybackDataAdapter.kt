@file:Suppress("ForbiddenComment")

package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.AppPlaybackItem
import de.chrgroth.spotify.control.domain.model.AppTrackData
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.PlaybackDataPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackDataRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.RecentlyPartialPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.RecentlyPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class PlaybackDataAdapter(
    private val userRepository: UserRepositoryPort,
    private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort,
    private val recentlyPartialPlayedRepository: RecentlyPartialPlayedRepositoryPort,
    private val appPlaybackRepository: AppPlaybackRepositoryPort,
    private val appTrackDataRepository: AppTrackDataRepositoryPort,
    private val outboxPort: OutboxPort,
) : PlaybackDataPort {

    override fun enqueueRebuild(userId: UserId) {
        logger.info { "Enqueuing playback data rebuild for user: ${userId.value}" }
        outboxPort.enqueue(DomainOutboxEvent.RebuildPlaybackData(userId))
    }

    override fun rebuildPlaybackData(userId: UserId) {
        logger.info { "Rebuilding playback data for user: ${userId.value}" }
        appPlaybackRepository.deleteAllByUserId(userId)
        appendPlaybackData(userId)
    }

    override fun appendPlaybackData(userId: UserId) {
        logger.info { "Appending playback data for user: ${userId.value}" }
        val since = appPlaybackRepository.findMostRecentPlayedAt(userId)

        val recentlyPlayed = recentlyPlayedRepository.findSince(userId, since)
        val partialPlayed = recentlyPartialPlayedRepository.findSince(userId, since)

        val allPlaybackItems = recentlyPlayed.map { item ->
            AppPlaybackItem(
                userId = item.spotifyUserId,
                playedAt = item.playedAt,
                trackId = item.trackId,
                // TODO: populate secondsPlayed from track duration once available in spotify_recently_played
                secondsPlayed = 0L,
            )
        } + partialPlayed.map { item ->
            AppPlaybackItem(
                userId = item.spotifyUserId,
                playedAt = item.playedAt,
                trackId = item.trackId,
                secondsPlayed = item.playedSeconds,
            )
        }

        if (allPlaybackItems.isEmpty()) {
            logger.info { "No new playback items to append for user: ${userId.value}" }
            return
        }

        val existingPlayedAts = appPlaybackRepository.findExistingPlayedAts(
            userId = userId,
            playedAts = allPlaybackItems.map { it.playedAt }.toSet(),
        )
        val newPlaybackItems = allPlaybackItems.filter { it.playedAt !in existingPlayedAts }

        if (newPlaybackItems.isEmpty()) {
            logger.info { "All playback items already exist for user: ${userId.value}" }
            return
        }

        val allTrackData = (recentlyPlayed.map { item ->
            AppTrackData(
                trackId = item.trackId,
                artistIds = item.artistIds,
                trackTitle = item.trackName,
                artistNames = item.artistNames,
            )
        } + partialPlayed.map { item ->
            AppTrackData(
                trackId = item.trackId,
                artistIds = item.artistIds,
                trackTitle = item.trackName,
                artistNames = item.artistNames,
            )
        }).distinctBy { it.trackId }

        appTrackDataRepository.upsertAll(allTrackData)

        logger.info { "Persisting ${newPlaybackItems.size} new app_playback items for user: ${userId.value}" }
        appPlaybackRepository.saveAll(newPlaybackItems)
    }

    companion object : KLogging()
}
