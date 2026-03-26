package de.chrgroth.spotify.control.domain.catalog

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.ArtistSettingsError
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistPlaybackProcessingStatus
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.catalog.SpotifyCatalogPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class CatalogService(
    private val spotifyAccessToken: SpotifyAccessTokenPort,
    private val spotifyCatalog: SpotifyCatalogPort,
    private val appArtistRepository: AppArtistRepositoryPort,
    private val appTrackRepository: AppTrackRepositoryPort,
    private val appAlbumRepository: AppAlbumRepositoryPort,
    private val appPlaybackRepository: AppPlaybackRepositoryPort,
    private val userRepository: UserRepositoryPort,
    private val outboxPort: OutboxPort,
    private val playlistRepository: PlaylistRepositoryPort,
    private val playlistCheckRepository: AppPlaylistCheckRepositoryPort,
    private val dashboardRefresh: DashboardRefreshPort,
    private val syncController: SyncController,
) : CatalogPort {

    // --- Artist Settings ---

    override fun findAllArtists(): List<AppArtist> = appArtistRepository.findAll()

    override fun findArtistsByStatus(status: ArtistPlaybackProcessingStatus, offset: Int, limit: Int): List<AppArtist> =
        appArtistRepository.findByPlaybackProcessingStatusPaged(status, offset, limit)

    override fun countArtistsByStatus(status: ArtistPlaybackProcessingStatus): Long =
        appArtistRepository.countByPlaybackProcessingStatus(status)

    override fun updateArtistPlaybackProcessingStatus(
        artistId: String,
        status: ArtistPlaybackProcessingStatus,
        userId: UserId,
    ): Either<DomainError, Unit> {
        val existing = appArtistRepository.findByArtistIds(setOf(ArtistId(artistId))).firstOrNull()
            ?: return ArtistSettingsError.ARTIST_NOT_FOUND.left()

        if (existing.playbackProcessingStatus == status) {
            logger.debug { "Artist $artistId already has status $status, skipping" }
            return Unit.right()
        }

        logger.info { "Updating playback processing status for artist $artistId to $status" }
        appArtistRepository.updatePlaybackProcessingStatus(ArtistId(artistId), status)

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
        val existing = appArtistRepository.findByArtistIds(setOf(ArtistId(artistId))).firstOrNull()
        if (existing != null) {
            logger.debug { "Artist $artistId already synced, skipping" }
            return Unit.right()
        }
        logger.info { "Fetching details for artist $artistId (user ${userId.value})" }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyCatalog.getArtist(userId, accessToken, artistId)
            .flatMap { detail ->
                if (detail != null) {
                    appArtistRepository.upsertAll(listOf(detail))
                    logger.info { "Updated sync data for artist $artistId" }
                    dashboardRefresh.notifyCatalogData()
                } else {
                    logger.warn { "No data returned from Spotify for artist $artistId" }
                }
                Unit.right()
            }
    }

    override fun resyncCatalog(): Either<DomainError, Unit> {
        val allArtistIds = appArtistRepository.findAll().map { it.id.value }
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
        appArtistRepository.findByArtistIds(setOf(ArtistId(artistId))).firstOrNull()
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
                    syncController.syncArtists(artistIds, userId)
                    val expectedTracks = albumResult.album.totalTracks
                    if (expectedTracks != null && albumResult.tracks.size < expectedTracks) {
                        logger.warn { "Album $albumId: synced ${albumResult.tracks.size} track(s) but album reports $expectedTracks total" }
                    }
                    dashboardRefresh.notifyCatalogData()
                }
                logger.info { "Synced album $albumId: ${albumResult.tracks.size} track(s)" }
                1.right()
            }
        }
    }

    // --- Outbox Handlers ---

    override fun handle(event: DomainOutboxEvent.SyncArtistDetails): Either<DomainError, Unit> =
        syncArtistDetails(event.artistId, event.userId)

    override fun handle(event: DomainOutboxEvent.SyncAlbumDetails): Either<DomainError, Unit> =
        syncAlbumDetails(event.albumId).map { Unit }

    override fun handle(event: DomainOutboxEvent.ResyncCatalog): Either<DomainError, Unit> =
        resyncCatalog()

    companion object : KLogging()
}
