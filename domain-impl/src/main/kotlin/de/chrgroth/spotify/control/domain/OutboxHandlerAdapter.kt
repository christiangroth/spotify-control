package de.chrgroth.spotify.control.domain

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.CurrentlyPlayingPort
import de.chrgroth.spotify.control.domain.port.`in`.OutboxHandlerPort
import de.chrgroth.spotify.control.domain.port.`in`.PlaybackDataPort
import de.chrgroth.spotify.control.domain.port.`in`.PlaybackEnrichmentPort
import de.chrgroth.spotify.control.domain.port.`in`.PlaylistSyncPort
import de.chrgroth.spotify.control.domain.port.`in`.RecentlyPlayedPort
import de.chrgroth.spotify.control.domain.port.`in`.UserProfileUpdatePort
import de.chrgroth.spotify.control.util.outbox.OutboxTaskResult
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class OutboxHandlerAdapter(
    private val currentlyPlaying: CurrentlyPlayingPort,
    private val recentlyPlayed: RecentlyPlayedPort,
    private val userProfileUpdate: UserProfileUpdatePort,
    private val playlistSync: PlaylistSyncPort,
    private val playbackData: PlaybackDataPort,
    private val playbackEnrichment: PlaybackEnrichmentPort,
) : OutboxHandlerPort {

    override fun handle(event: DomainOutboxEvent.FetchCurrentlyPlaying): OutboxTaskResult = try {
        when (val result = currentlyPlaying.update(event.userId)) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> OutboxTaskResult.RateLimited(error.retryAfter)
                else -> {
                    logger.error { "Failed to fetch currently playing for user ${event.userId.value}: ${error.code}" }
                    OutboxTaskResult.Failed("Failed to fetch currently playing: ${error.code}")
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(FetchCurrentlyPlaying) for user ${event.userId.value}" }
        OutboxTaskResult.Failed("Unexpected error in update: ${e.message}", e)
    }

    override fun handle(event: DomainOutboxEvent.FetchRecentlyPlayed): OutboxTaskResult = try {
        when (val result = recentlyPlayed.update(event.userId)) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> OutboxTaskResult.RateLimited(error.retryAfter)
                else -> {
                    logger.error { "Failed to fetch recently played for user ${event.userId.value}: ${error.code}" }
                    OutboxTaskResult.Failed("Failed to fetch recently played: ${error.code}")
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(FetchRecentlyPlayed) for user ${event.userId.value}" }
        OutboxTaskResult.Failed("Unexpected error in update: ${e.message}", e)
    }

    override fun handle(event: DomainOutboxEvent.UpdateUserProfile): OutboxTaskResult = try {
        when (val result = userProfileUpdate.update(event.userId)) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> OutboxTaskResult.RateLimited(error.retryAfter)
                else -> {
                    logger.error { "Failed to update user profile for ${event.userId.value}: ${error.code}" }
                    OutboxTaskResult.Failed("Failed to update user profile: ${error.code}")
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(UpdateUserProfile) for user ${event.userId.value}" }
        OutboxTaskResult.Failed("Unexpected error in update: ${e.message}", e)
    }

    override fun handle(event: DomainOutboxEvent.SyncPlaylistInfo): OutboxTaskResult = try {
        when (val result = playlistSync.syncPlaylists(event.userId)) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> OutboxTaskResult.RateLimited(error.retryAfter)
                else -> {
                    logger.error { "Failed to sync playlists for user ${event.userId.value}: ${error.code}" }
                    OutboxTaskResult.Failed("Failed to sync playlists: ${error.code}")
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(SyncPlaylistInfo) for user ${event.userId.value}" }
        OutboxTaskResult.Failed("Unexpected error in sync: ${e.message}", e)
    }

    override fun handle(event: DomainOutboxEvent.SyncPlaylistData): OutboxTaskResult = try {
        when (val result = playlistSync.syncPlaylistData(event.userId, event.playlistId)) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> OutboxTaskResult.RateLimited(error.retryAfter)
                else -> {
                    logger.error { "Failed to sync playlist data for playlist ${event.playlistId} (user ${event.userId.value}): ${error.code}" }
                    OutboxTaskResult.Failed("Failed to sync playlist data: ${error.code}")
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(SyncPlaylistData) for playlist ${event.playlistId} (user ${event.userId.value})" }
        OutboxTaskResult.Failed("Unexpected error in sync: ${e.message}", e)
    }

    override fun handle(event: DomainOutboxEvent.RebuildPlaybackData): OutboxTaskResult = try {
        playbackData.rebuildPlaybackData(event.userId)
        OutboxTaskResult.Success
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(RebuildPlaybackData) for user ${event.userId.value}" }
        OutboxTaskResult.Failed("Unexpected error in rebuild: ${e.message}", e)
    }

    override fun handle(event: DomainOutboxEvent.AppendPlaybackData): OutboxTaskResult = try {
        playbackData.appendPlaybackData(event.userId)
        OutboxTaskResult.Success
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(AppendPlaybackData) for user ${event.userId.value}" }
        OutboxTaskResult.Failed("Unexpected error in append: ${e.message}", e)
    }

    override fun handle(event: DomainOutboxEvent.EnrichArtistData): OutboxTaskResult = try {
        when (val result = playbackEnrichment.enrichArtistData(event.userId)) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> OutboxTaskResult.RateLimited(error.retryAfter)
                else -> {
                    logger.error { "Failed to enrich artist data for user ${event.userId.value}: ${error.code}" }
                    OutboxTaskResult.Failed("Failed to enrich artist data: ${error.code}")
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(EnrichArtistData) for user ${event.userId.value}" }
        OutboxTaskResult.Failed("Unexpected error in enrich: ${e.message}", e)
    }

    override fun handle(event: DomainOutboxEvent.EnrichTrackData): OutboxTaskResult = try {
        when (val result = playbackEnrichment.enrichTrackData(event.userId)) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> OutboxTaskResult.RateLimited(error.retryAfter)
                else -> {
                    logger.error { "Failed to enrich track data for user ${event.userId.value}: ${error.code}" }
                    OutboxTaskResult.Failed("Failed to enrich track data: ${error.code}")
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(EnrichTrackData) for user ${event.userId.value}" }
        OutboxTaskResult.Failed("Unexpected error in enrich: ${e.message}", e)
    }

    companion object : KLogging()
}
