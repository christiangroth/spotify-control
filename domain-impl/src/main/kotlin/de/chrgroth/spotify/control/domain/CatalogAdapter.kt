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
import de.chrgroth.spotify.control.domain.model.TrackId
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

    override fun syncArtistDetails(artistId: String, userId: UserId): Either<DomainError, Unit> {
        val existing = appArtistRepository.findByArtistIds(setOf(artistId)).firstOrNull()
        if (existing?.lastSync != null && existing.artistName.isNotBlank()) {
            logger.debug { "Artist $artistId already synced, skipping" }
            return Unit.right()
        }
        logger.info { "Fetching genre details for artist $artistId (user ${userId.value})" }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyCatalog.getArtist(userId, accessToken, artistId)
            .flatMap { detail ->
                if (detail != null) {
                    appArtistRepository.updateSyncData(detail.artistId, detail.artistName, detail.genre, detail.additionalGenres, detail.imageLink, detail.type)
                    logger.info { "Updated sync data for artist $artistId: genre=${detail.genre}, additionalGenres=${detail.additionalGenres}" }
                } else {
                    logger.warn { "No data returned from Spotify for artist $artistId" }
                }
                Unit.right()
            }
    }

    override fun syncTrackDetails(trackId: String, userId: UserId): Either<DomainError, Unit> {
        val existing = appTrackRepository.findByTrackIds(setOf(TrackId(trackId))).firstOrNull()
        if (existing?.lastSync != null) {
            logger.debug { "Track $trackId already synced, skipping" }
            return Unit.right()
        }
        logger.info { "Fetching track/album details for track $trackId (user ${userId.value})" }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyCatalog.getTrack(userId, accessToken, trackId)
            .flatMap { result ->
                if (result != null) {
                    appTrackRepository.updateTrackSyncData(result.track)
                    appAlbumRepository.upsertAll(listOf(result.album))
                    val allArtistIds = (listOf(result.track.artistId) + result.track.additionalArtistIds)
                        .map { it.value }
                        .filter { it.isNotBlank() }
                    allArtistIds.forEach { artistId ->
                        outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails(artistId, userId))
                    }
                    logger.info { "Updated sync data for track $trackId → album ${result.album.id.value}" }
                } else {
                    logger.warn { "No data returned from Spotify for track $trackId" }
                }
                Unit.right()
            }
    }

    override fun syncMissingArtists(): Either<DomainError, Int> {
        val userId = userRepository.findAll().firstOrNull()?.spotifyUserId
        if (userId == null) {
            logger.debug { "No users available, skipping syncMissingArtists" }
            return 0.right()
        }
        val artistIds = syncPoolRepository.popArtists(BULK_LIMIT)
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
                }
                val syncedIds = artists.map { it.artistId }.toSet()
                val failedIds = artistIds.filterNot { it in syncedIds }
                if (failedIds.isNotEmpty()) {
                    logger.warn { "Bulk artist sync returned no data for ${failedIds.size} IDs, falling back to per-item sync" }
                    fallbackSyncArtists(userId, failedIds)
                }
                artists.size.right()
            }
    }

    override fun syncMissingTracks(): Either<DomainError, Int> {
        val userId = userRepository.findAll().firstOrNull()?.spotifyUserId
        if (userId == null) {
            logger.debug { "No users available, skipping syncMissingTracks" }
            return 0.right()
        }
        val trackIds = syncPoolRepository.popTracks(BULK_LIMIT)
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
                    val artistIds = results.flatMap { result ->
                        (listOf(result.track.artistId) + result.track.additionalArtistIds).map { it.value }
                    }.filter { it.isNotBlank() }.distinct()
                    if (artistIds.isNotEmpty()) {
                        syncPoolRepository.addArtists(artistIds)
                    }
                }
                val syncedIds = results.map { it.track.id.value }.toSet()
                val failedIds = trackIds.filterNot { it in syncedIds }
                if (failedIds.isNotEmpty()) {
                    logger.warn { "Bulk track sync returned no data for ${failedIds.size} IDs, falling back to per-item sync" }
                    fallbackSyncTracks(userId, failedIds)
                }
                results.size.right()
            }
    }

    private fun fallbackSyncArtists(userId: UserId, artistIds: List<String>) {
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        artistIds.forEach { artistId ->
            when (val result = spotifyCatalog.getArtist(userId, accessToken, artistId)) {
                is Either.Right -> result.value?.let { artist ->
                    appArtistRepository.upsertAll(listOf(artist))
                    logger.info { "Fallback: synced artist $artistId" }
                } ?: logger.warn { "Fallback: no data from Spotify for artist $artistId" }
                is Either.Left -> logger.error { "Fallback: failed to sync artist $artistId: ${result.value.code}" }
            }
        }
    }

    private fun fallbackSyncTracks(userId: UserId, trackIds: List<String>) {
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        trackIds.forEach { trackId ->
            when (val result = spotifyCatalog.getTrack(userId, accessToken, trackId)) {
                is Either.Right -> result.value?.let { syncResult ->
                    appTrackRepository.upsertAll(listOf(syncResult.track))
                    appAlbumRepository.upsertAll(listOf(syncResult.album))
                    logger.info { "Fallback: synced track $trackId" }
                } ?: logger.warn { "Fallback: no data from Spotify for track $trackId" }
                is Either.Left -> logger.error { "Fallback: failed to sync track $trackId: ${result.value.code}" }
            }
        }
    }

    // --- Outbox Handlers ---

    override fun handle(event: DomainOutboxEvent.SyncArtistDetails): OutboxTaskResult = try {
        when (val result = syncArtistDetails(event.artistId, event.userId)) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> {
                    logger.warn { "Rate limited on SyncArtistDetails artist ${event.artistId} (user ${event.userId.value}), retry after ${error.retryAfter.seconds}s" }
                    OutboxTaskResult.RateLimited(error.retryAfter)
                }
                else -> {
                    logger.error { "Failed to sync artist ${event.artistId} for user ${event.userId.value}: ${error.code}" }
                    OutboxTaskResult.Failed("Failed to sync artist: ${error.code}")
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(SyncArtistDetails) for artist ${event.artistId} (user ${event.userId.value})" }
        OutboxTaskResult.Failed("Unexpected error in sync: ${e.message}", e)
    }

    override fun handle(event: DomainOutboxEvent.SyncTrackDetails): OutboxTaskResult = try {
        when (val result = syncTrackDetails(event.trackId, event.userId)) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> {
                    logger.warn { "Rate limited on SyncTrackDetails for track ${event.trackId} (user ${event.userId.value}), retry after ${error.retryAfter.seconds}s" }
                    OutboxTaskResult.RateLimited(error.retryAfter)
                }
                else -> {
                    logger.error { "Failed to sync track ${event.trackId} for user ${event.userId.value}: ${error.code}" }
                    OutboxTaskResult.Failed("Failed to sync track: ${error.code}")
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(SyncTrackDetails) for track ${event.trackId} (user ${event.userId.value})" }
        OutboxTaskResult.Failed("Unexpected error in sync: ${e.message}", e)
    }

    companion object : KLogging() {
        private const val BULK_LIMIT = 50
    }
}
