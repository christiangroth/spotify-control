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
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus
import de.chrgroth.spotify.control.domain.model.ArtistId
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.CatalogPort
import de.chrgroth.spotify.control.domain.port.out.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxManagementPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
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
    private val outboxManagement: OutboxManagementPort,
    private val playlistRepository: PlaylistRepositoryPort,
    private val playlistCheckRepository: AppPlaylistCheckRepositoryPort,
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
        if (existing != null) {
            logger.debug { "Artist $artistId already synced, skipping" }
            return Unit.right()
        }
        logger.info { "Fetching genre details for artist $artistId (user ${userId.value})" }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyCatalog.getArtist(userId, accessToken, artistId)
            .flatMap { detail ->
                if (detail != null) {
                    appArtistRepository.upsertAll(listOf(detail))
                    logger.info { "Updated sync data for artist $artistId: genre=${detail.genre}, additionalGenres=${detail.additionalGenres}" }
                } else {
                    logger.warn { "No data returned from Spotify for artist $artistId" }
                }
                Unit.right()
            }
    }

    override fun resyncCatalog(): Either<DomainError, Unit> {
        val allArtistIds = appArtistRepository.findAll().map { it.artistId }
        val allTracks = appTrackRepository.findAll()
        val allAlbumIds = allTracks.mapNotNull { it.albumId?.value }.distinct()
        val userId = userRepository.findAll().firstOrNull()?.spotifyUserId
        logger.info { "Re-syncing catalog: ${allArtistIds.size} artist(s) and ${allAlbumIds.size} album(s)" }
        if (userId != null) {
            allArtistIds.forEach { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails(it, userId)) }
        }
        allAlbumIds.forEach { outboxPort.enqueue(DomainOutboxEvent.SyncAlbumDetails(it)) }
        return Unit.right()
    }

    override fun resyncArtist(artistId: String): Either<DomainError, Unit> {
        appArtistRepository.findByArtistIds(setOf(artistId)).firstOrNull()
            ?: return ArtistSettingsError.ARTIST_NOT_FOUND.left()
        val userId = userRepository.findAll().firstOrNull()?.spotifyUserId ?: run {
            logger.warn { "No users available for artist resync, skipping $artistId" }
            return Unit.right()
        }
        logger.info { "Re-syncing artist $artistId and all their albums" }
        outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails(artistId, userId))
        val albumIds = appTrackRepository.findByArtistId(ArtistId(artistId))
            .mapNotNull { it.albumId?.value }
            .distinct()
        albumIds.forEach { outboxPort.enqueue(DomainOutboxEvent.SyncAlbumDetails(it)) }
        return Unit.right()
    }

    override fun wipeCatalog(): Either<DomainError, Unit> {
        logger.info { "Wiping all catalog data" }
        appArtistRepository.deleteAll()
        appAlbumRepository.deleteAll()
        appTrackRepository.deleteAll()
        outboxManagement.deleteByEventTypes(CATALOG_OUTBOX_EVENT_KEYS)
        playlistRepository.setAllSyncInactive()
        playlistCheckRepository.deleteAll()
        logger.info { "Catalog wipe complete" }
        return Unit.right()
    }

    private fun syncAlbumDetails(albumId: String): Either<DomainError, Int> {
        val userId = userRepository.findAll().firstOrNull()?.spotifyUserId
        if (userId == null) {
            logger.debug { "No users available, skipping syncAlbumDetails" }
            return 0.right()
        }
        logger.info { "Syncing album $albumId" }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        val result = spotifyCatalog.getAlbum(userId, accessToken, albumId)
        return when (result) {
            is Either.Left -> result.value.left()
            is Either.Right -> {
                val albumResult = result.value
                if (albumResult.tracks.isNotEmpty()) {
                    appTrackRepository.upsertAll(albumResult.tracks)
                    appAlbumRepository.upsertAll(listOf(albumResult.album))
                    val artistIds = albumResult.tracks
                        .flatMap { t -> (listOf(t.artistId) + t.additionalArtistIds).map { it.value } }
                        .filter { it.isNotBlank() }.distinct()
                    artistIds.filter { it !in existingArtistIds }
                        .forEach { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails(it, userId)) }
                    val expectedTracks = albumResult.album.totalTracks
                    if (expectedTracks != null && albumResult.tracks.size < expectedTracks) {
                        logger.warn { "Album $albumId: synced ${albumResult.tracks.size} track(s) but album reports $expectedTracks total" }
                    }
                }
                logger.info { "Synced album $albumId: ${albumResult.tracks.size} track(s)" }
                1.right()
            }
        }
    }

    // --- Outbox Handlers ---

    override fun handle(event: DomainOutboxEvent.SyncArtistDetails): OutboxTaskResult =
        handleOutboxTask("SyncArtistDetails[artist=${event.artistId},user=${event.userId.value}]") {
            syncArtistDetails(event.artistId, event.userId)
        }

    override fun handle(event: DomainOutboxEvent.SyncAlbumDetails): OutboxTaskResult =
        handleOutboxTask("SyncAlbumDetails[album=${event.albumId}]") { syncAlbumDetails(event.albumId) }

    override fun handle(event: DomainOutboxEvent.ResyncCatalog): OutboxTaskResult =
        handleOutboxTask("ResyncCatalog") { resyncCatalog() }

    private fun handleOutboxTask(taskDescription: String, operation: () -> Either<DomainError, *>): OutboxTaskResult = try {
        when (val result = operation()) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> {
                    logger.warn { "Rate limited on $taskDescription, retry after ${error.retryAfter.seconds}s" }
                    OutboxTaskResult.RateLimited(error.retryAfter)
                }
                else -> {
                    logger.error { "Failed $taskDescription: ${error.code}" }
                    OutboxTaskResult.Failed("Failed $taskDescription: ${error.code}")
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in $taskDescription" }
        OutboxTaskResult.Failed("Unexpected error in $taskDescription: ${e.message}", e)
    }

    companion object : KLogging() {
        private val CATALOG_OUTBOX_EVENT_KEYS = listOf(
            DomainOutboxEvent.SyncArtistDetails.KEY,
            DomainOutboxEvent.SyncArtistDetails.LEGACY_KEY,
            DomainOutboxEvent.SyncAlbumDetails.KEY,
            DomainOutboxEvent.ResyncCatalog.KEY,
        )
    }
}
