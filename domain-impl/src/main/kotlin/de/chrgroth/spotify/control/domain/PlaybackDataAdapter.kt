@file:Suppress("ForbiddenComment")

package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.AppPlaybackItem
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus
import de.chrgroth.spotify.control.domain.model.RecentlyPartialPlayedItem
import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.PlaybackDataPort
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.RecentlyPartialPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.RecentlyPlayedRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class PlaybackDataAdapter(
    private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort,
    private val recentlyPartialPlayedRepository: RecentlyPartialPlayedRepositoryPort,
    private val appPlaybackRepository: AppPlaybackRepositoryPort,
    private val appArtistRepository: AppArtistRepositoryPort,
    private val appEnrichmentService: AppEnrichmentService,
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

        val inactiveArtistIds = appArtistRepository.findByPlaybackProcessingStatus(ArtistPlaybackProcessingStatus.INACTIVE)
            .map { it.artistId }
            .toSet()

        val filteredRecentlyPlayed = recentlyPlayed.filter { it.artistIds.firstOrNull() !in inactiveArtistIds }
        val filteredPartialPlayed = partialPlayed.filter { it.artistIds.firstOrNull() !in inactiveArtistIds }

        val allPlaybackItems = buildPlaybackItems(filteredRecentlyPlayed, filteredPartialPlayed)
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

        val artists = buildArtists(filteredRecentlyPlayed, filteredPartialPlayed)
        val tracks = buildTracks(filteredRecentlyPlayed, filteredPartialPlayed)

        logger.info { "Persisting ${newPlaybackItems.size} new app_playback items for user: ${userId.value}" }
        appPlaybackRepository.saveAll(newPlaybackItems)

        // Upsert entity stubs and enqueue the full three-stage enrichment pipeline.
        appEnrichmentService.upsertAndEnqueueEnrichment(artists, tracks, userId)
    }

    private fun buildPlaybackItems(
        recentlyPlayed: List<RecentlyPlayedItem>,
        partialPlayed: List<RecentlyPartialPlayedItem>,
    ) = recentlyPlayed.map { item ->
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

    private fun buildArtists(
        recentlyPlayed: List<RecentlyPlayedItem>,
        partialPlayed: List<RecentlyPartialPlayedItem>,
    ) = (recentlyPlayed.flatMap { item ->
        item.artistIds.mapIndexedNotNull { index, artistId ->
            val name = item.artistNames.getOrNull(index) ?: return@mapIndexedNotNull null
            AppArtist(artistId = artistId, artistName = name)
        }
    } + partialPlayed.flatMap { item ->
        item.artistIds.mapIndexedNotNull { index, artistId ->
            val name = item.artistNames.getOrNull(index) ?: return@mapIndexedNotNull null
            AppArtist(artistId = artistId, artistName = name)
        }
    }).distinctBy { it.artistId }

    private fun buildTracks(
        recentlyPlayed: List<RecentlyPlayedItem>,
        partialPlayed: List<RecentlyPartialPlayedItem>,
    ) = (recentlyPlayed.mapNotNull { item ->
        val artistId = item.artistIds.firstOrNull() ?: return@mapNotNull null
        AppTrack(trackId = item.trackId, trackTitle = item.trackName, artistId = artistId, additionalArtistIds = item.artistIds.drop(1))
    } + partialPlayed.mapNotNull { item ->
        val artistId = item.artistIds.firstOrNull() ?: return@mapNotNull null
        AppTrack(trackId = item.trackId, trackTitle = item.trackName, artistId = artistId, additionalArtistIds = item.artistIds.drop(1))
    }).distinctBy { it.trackId }

    companion object : KLogging()
}
