package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.outbox.OutboxTaskResult
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.PlaylistSyncError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.ArtistId
import de.chrgroth.spotify.control.domain.model.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.PlaylistPort
import de.chrgroth.spotify.control.domain.port.out.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyPlaylistPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import kotlin.time.Clock

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class PlaylistAdapter(
    private val userRepository: UserRepositoryPort,
    private val playlistRepository: PlaylistRepositoryPort,
    private val spotifyAccessToken: SpotifyAccessTokenPort,
    private val spotifyPlaylist: SpotifyPlaylistPort,
    private val outboxPort: OutboxPort,
    private val dashboardRefresh: DashboardRefreshPort,
    private val appSyncService: AppSyncService,
) : PlaylistPort {

    override fun enqueueUpdates() {
        val users = userRepository.findAll()
        logger.info { "Scheduling playlist sync for ${users.size} user(s)" }
        users.forEach { user ->
            outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistInfo(user.spotifyUserId))
        }
    }

    override fun syncPlaylists(userId: UserId): Either<DomainError, Unit> {
        userRepository.findById(userId) ?: run {
            logger.warn { "User not found for playlist sync: ${userId.value}" }
            return Unit.right()
        }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyPlaylist.getPlaylists(userId, accessToken).map { spotifyPlaylists ->
            val now = Clock.System.now()
            val existingById = playlistRepository.findByUserId(userId).associateBy { it.spotifyPlaylistId }
            val updatedPlaylists = spotifyPlaylists.filter { it.ownerId == userId.value }.map { item ->
                val existing = existingById[item.id]
                PlaylistInfo(
                    spotifyPlaylistId = item.id,
                    snapshotId = item.snapshotId,
                    lastSnapshotIdSyncTime = if (existing == null || existing.snapshotId != item.snapshotId) now else existing.lastSnapshotIdSyncTime,
                    name = item.name,
                    syncStatus = existing?.syncStatus ?: PlaylistSyncStatus.PASSIVE,
                )
            }
            logger.info { "Synced ${updatedPlaylists.size} playlist(s) for user ${userId.value}" }
            playlistRepository.saveAll(userId, updatedPlaylists)
            if (updatedPlaylists.size != existingById.size) {
                dashboardRefresh.notifyUserPlaylistMetadata(userId)
            }
            updatedPlaylists
                .filter { it.syncStatus == PlaylistSyncStatus.ACTIVE }
                .filter { playlist ->
                    val existing = existingById[playlist.spotifyPlaylistId]
                    existing == null ||
                        existing.snapshotId != playlist.snapshotId ||
                        playlistRepository.findByUserIdAndPlaylistId(userId, playlist.spotifyPlaylistId) == null
                }
                .forEach { playlist ->
                    logger.info { "Enqueueing SyncPlaylistData for active playlist ${playlist.spotifyPlaylistId} (user ${userId.value})" }
                    outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(userId, playlist.spotifyPlaylistId))
                }
        }
    }

    override fun syncPlaylistData(userId: UserId, playlistId: String): Either<DomainError, Unit> {
        userRepository.findById(userId) ?: run {
            logger.warn { "User not found for playlist data sync: ${userId.value}" }
            return Unit.right()
        }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyPlaylist.getPlaylistTracks(userId, accessToken, playlistId).map { playlist ->
            logger.info { "Synced ${playlist.tracks.size} track(s) for playlist $playlistId (user ${userId.value})" }
            playlistRepository.save(userId, playlist)

            val artistStubs = playlist.tracks
                .flatMap { track -> track.artistIds.zip(track.artistNames) }
                .distinctBy { (artistId, _) -> artistId }
                .map { (artistId, artistName) -> AppArtist(artistId = artistId, artistName = artistName) }

            val trackStubs = playlist.tracks.mapNotNull { track ->
                val artistId = track.artistIds.firstOrNull() ?: run {
                    logger.warn { "Skipping track ${track.trackId} in playlist $playlistId: no artist data available" }
                    return@mapNotNull null
                }
                AppTrack(
                    id = TrackId(track.trackId),
                    title = track.trackName,
                    artistId = ArtistId(artistId),
                    additionalArtistIds = track.artistIds.drop(1).map { ArtistId(it) },
                )
            }

            appSyncService.upsertAndAddToSyncPool(artistStubs, trackStubs, userId)
        }
    }

    override fun updateSyncStatus(userId: UserId, playlistId: String, syncStatus: PlaylistSyncStatus): Either<DomainError, Unit> {
        userRepository.findById(userId) ?: run {
            logger.warn { "User not found for playlist sync status update: ${userId.value}" }
            return PlaylistSyncError.PLAYLIST_NOT_FOUND.left()
        }
        val playlists = playlistRepository.findByUserId(userId)
        val playlist = playlists.find { it.spotifyPlaylistId == playlistId } ?: run {
            logger.warn { "Playlist $playlistId not found for user ${userId.value}" }
            return PlaylistSyncError.PLAYLIST_NOT_FOUND.left()
        }
        val updatedPlaylists = playlists.map {
            if (it.spotifyPlaylistId == playlistId) it.copy(syncStatus = syncStatus) else it
        }
        logger.info { "Updated sync status for playlist $playlistId (user ${userId.value}) to $syncStatus" }
        playlistRepository.saveAll(userId, updatedPlaylists)
        dashboardRefresh.notifyUserPlaylistMetadata(userId)
        if (syncStatus == PlaylistSyncStatus.ACTIVE &&
            playlistRepository.findByUserIdAndPlaylistId(userId, playlist.spotifyPlaylistId) == null
        ) {
            logger.info { "Enqueueing SyncPlaylistData for newly active playlist $playlistId (user ${userId.value})" }
            outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(userId, playlistId))
        }
        return Unit.right()
    }

    override fun enqueueSyncPlaylistData(userId: UserId, playlistId: String): Either<DomainError, Unit> {
        userRepository.findById(userId) ?: run {
            logger.warn { "User not found for playlist data sync enqueue: ${userId.value}" }
            return PlaylistSyncError.PLAYLIST_NOT_FOUND.left()
        }
        val playlists = playlistRepository.findByUserId(userId)
        val playlist = playlists.find { it.spotifyPlaylistId == playlistId } ?: run {
            logger.warn { "Playlist $playlistId not found for user ${userId.value}" }
            return PlaylistSyncError.PLAYLIST_NOT_FOUND.left()
        }
        return if (playlist.syncStatus != PlaylistSyncStatus.ACTIVE) {
            logger.warn { "Playlist $playlistId is not active for user ${userId.value}, skipping sync enqueue" }
            PlaylistSyncError.PLAYLIST_SYNC_INACTIVE.left()
        } else {
            logger.info { "Enqueueing SyncPlaylistData for playlist $playlistId (user ${userId.value})" }
            outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(userId, playlistId))
            Unit.right()
        }
    }

    override fun handle(event: DomainOutboxEvent.SyncPlaylistInfo): OutboxTaskResult = try {
        when (val result = syncPlaylists(event.userId)) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> {
                    logger.warn { "Rate limited on SyncPlaylistInfo for user ${event.userId.value}, retry after ${error.retryAfter.seconds}s" }
                    OutboxTaskResult.RateLimited(error.retryAfter)
                }
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
        when (val result = syncPlaylistData(event.userId, event.playlistId)) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> {
                    logger.warn { "Rate limited on SyncPlaylistData playlist ${event.playlistId} (user ${event.userId.value}), retry after ${error.retryAfter.seconds}s" }
                    OutboxTaskResult.RateLimited(error.retryAfter)
                }
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

    companion object : KLogging()
}
