package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import de.chrgroth.outbox.OutboxTaskResult
import de.chrgroth.spotify.control.domain.error.ArtistSettingsError
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus
import de.chrgroth.spotify.control.domain.model.ArtistId
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.CatalogPort
import de.chrgroth.spotify.control.domain.port.out.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppSyncPoolRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyCatalogPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class CatalogAdapter(
    private val spotifyAccessToken: SpotifyAccessTokenPort,
    private val spotifyCatalog: SpotifyCatalogPort,
    private val appArtistRepository: AppArtistRepositoryPort,
    private val appTrackRepository: AppTrackRepositoryPort,
    private val appAlbumRepository: AppAlbumRepositoryPort,
    private val appPlaybackRepository: AppPlaybackRepositoryPort,
    private val userRepository: UserRepositoryPort,
    private val outboxPort: OutboxPort,
    private val syncPoolRepository: AppSyncPoolRepositoryPort,
) : CatalogPort {

    // --- Artist Settings ---

    override fun findAllArtists(): List<AppArtist> = appArtistRepository.findAll()

    override fun updateArtistPlaybackProcessingStatus(
        artistId: String,
        status: ArtistPlaybackProcessingStatus,
        userId: UserId,
    ): Either<DomainError, Unit> {
        val existing = appArtistRepository.findByArtistIds(setOf(artistId)).firstOrNull()
            ?: return ArtistSettingsError.ARTIST_NOT_FOUND.left()

        if (existing.playbackProcessingStatus == status) {
            logger.debug { "Artist $artistId already has status $status, skipping" }
            return Unit.right()
        }

        logger.info { "Updating playback processing status for artist $artistId to $status" }
        appArtistRepository.updatePlaybackProcessingStatus(artistId, status)

        when (status) {
            ArtistPlaybackProcessingStatus.INACTIVE -> {
                val trackIds = appTrackRepository.findByArtistId(ArtistId(artistId)).map { it.id.value }.toSet()
                if (trackIds.isNotEmpty()) {
                    logger.info { "Deleting app_playback for ${trackIds.size} tracks of artist $artistId" }
                    appPlaybackRepository.deleteAllByTrackIds(trackIds)
                }
            }
            ArtistPlaybackProcessingStatus.ACTIVE, ArtistPlaybackProcessingStatus.UNDECIDED -> {
                if (existing.playbackProcessingStatus == ArtistPlaybackProcessingStatus.INACTIVE) {
                    logger.info { "Artist $artistId reactivated, enqueueing RebuildPlaybackData for all users" }
                    userRepository.findAll().forEach { user ->
                        outboxPort.enqueue(DomainOutboxEvent.RebuildPlaybackData(user.spotifyUserId))
                    }
                }
            }
        }

        return Unit.right()
    }

    // --- Catalog Sync ---

    private fun syncMissingArtists(): Either<DomainError, Int> {
        val userId = userRepository.findAll().firstOrNull()?.spotifyUserId
        if (userId == null) {
            logger.debug { "No users available, skipping syncMissingArtists" }
            return 0.right()
        }
        val artistIds = syncPoolRepository.peekArtists(BULK_LIMIT)
        if (artistIds.isEmpty()) {
            logger.debug { "No artists in sync pool" }
            return 0.right()
        }
        logger.info { "Syncing ${artistIds.size} missing artists from pool" }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyCatalog.getArtists(userId, accessToken, artistIds)
            .flatMap { artists ->
                if (artists.isNotEmpty()) {
                    appArtistRepository.upsertAll(artists)
                    val syncedIds = artists.map { it.artistId }
                    syncPoolRepository.removeArtists(syncedIds)
                    logger.info { "Synced ${artists.size} artists; ${artistIds.size - artists.size} not returned by Spotify will be retried" }
                }
                artists.size.right()
            }
    }

    private fun syncMissingTracks(): Either<DomainError, Int> {
        val userId = userRepository.findAll().firstOrNull()?.spotifyUserId
        if (userId == null) {
            logger.debug { "No users available, skipping syncMissingTracks" }
            return 0.right()
        }
        val trackIds = syncPoolRepository.peekTracks(BULK_LIMIT)
        if (trackIds.isEmpty()) {
            logger.debug { "No tracks in sync pool" }
            return 0.right()
        }
        logger.info { "Syncing ${trackIds.size} missing tracks from pool" }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyCatalog.getTracks(userId, accessToken, trackIds)
            .flatMap { results ->
                if (results.isNotEmpty()) {
                    appTrackRepository.upsertAll(results.map { it.track })
                    appAlbumRepository.upsertAll(results.map { it.album })
                    val syncedTrackIds = results.map { it.track.id.value }
                    syncPoolRepository.removeTracks(syncedTrackIds)
                    val artistIds = results.flatMap { result ->
                        (listOf(result.track.artistId) + result.track.additionalArtistIds).map { it.value }
                    }.filter { it.isNotBlank() }.distinct()
                    if (artistIds.isNotEmpty()) {
                        syncPoolRepository.addArtists(artistIds)
                    }
                    logger.info { "Synced ${results.size} tracks; ${trackIds.size - results.size} not returned by Spotify will be retried" }
                }
                results.size.right()
            }
    }

    // --- Outbox Handlers ---

    override fun handle(event: DomainOutboxEvent.SyncMissingArtists): OutboxTaskResult = try {
        when (val result = syncMissingArtists()) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> {
                    logger.warn { "Rate limited on SyncMissingArtists, retry after ${error.retryAfter.seconds}s" }
                    OutboxTaskResult.RateLimited(error.retryAfter)
                }
                else -> {
                    logger.error { "Failed to sync missing artists: ${error.code}" }
                    OutboxTaskResult.Failed("Failed to sync missing artists: ${error.code}")
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(SyncMissingArtists)" }
        OutboxTaskResult.Failed("Unexpected error in sync: ${e.message}", e)
    }

    override fun handle(event: DomainOutboxEvent.SyncMissingTracks): OutboxTaskResult = try {
        when (val result = syncMissingTracks()) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> {
                    logger.warn { "Rate limited on SyncMissingTracks, retry after ${error.retryAfter.seconds}s" }
                    OutboxTaskResult.RateLimited(error.retryAfter)
                }
                else -> {
                    logger.error { "Failed to sync missing tracks: ${error.code}" }
                    OutboxTaskResult.Failed("Failed to sync missing tracks: ${error.code}")
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(SyncMissingTracks)" }
        OutboxTaskResult.Failed("Unexpected error in sync: ${e.message}", e)
    }

    companion object : KLogging() {
        private const val BULK_LIMIT = 50
    }
}
