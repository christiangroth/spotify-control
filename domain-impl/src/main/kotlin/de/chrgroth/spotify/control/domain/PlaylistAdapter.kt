package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.outbox.OutboxTaskResult
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.PlaylistSyncError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.model.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.PlaylistType
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.PlaylistPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaylistCheckRepositoryPort
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
    private val playlistCheckRepository: AppPlaylistCheckRepositoryPort,
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
                    type = existing?.type,
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

            val artistIds = playlist.tracks.flatMap { it.artistIds }.distinct()
            val albumIds = playlist.tracks.map { it.albumId }.distinct()
            artistIds.forEach { artistId -> outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails(artistId, userId)) }
            albumIds.forEach { albumId -> outboxPort.enqueue(DomainOutboxEvent.SyncAlbumDetails(albumId)) }
            outboxPort.enqueue(DomainOutboxEvent.RunPlaylistChecks(userId, playlistId))
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
            if (it.spotifyPlaylistId == playlistId) {
                val newType = when {
                    syncStatus == PlaylistSyncStatus.PASSIVE -> null
                    it.type != null -> it.type
                    it.name.matches(YEAR_NAME_REGEX) -> PlaylistType.YEAR
                    else -> PlaylistType.UNKNOWN
                }
                it.copy(syncStatus = syncStatus, type = newType)
            } else {
                it
            }
        }
        logger.info { "Updated sync status for playlist $playlistId (user ${userId.value}) to $syncStatus" }
        playlistRepository.saveAll(userId, updatedPlaylists)
        dashboardRefresh.notifyUserPlaylistMetadata(userId)
        if (syncStatus == PlaylistSyncStatus.PASSIVE) {
            logger.info { "Deleting checks for deactivated playlist $playlistId (user ${userId.value})" }
            playlistCheckRepository.deleteByPlaylistId(playlistId)
        } else if (syncStatus == PlaylistSyncStatus.ACTIVE) {
            logger.info { "Enqueueing SyncPlaylistData for activated playlist $playlistId (user ${userId.value})" }
            outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(userId, playlistId))
        }
        return Unit.right()
    }

    override fun updatePlaylistType(userId: UserId, playlistId: String, type: PlaylistType): Either<DomainError, Unit> {
        val validationError = validatePlaylistTypeUpdate(userId, playlistId, type)
        if (validationError != null) return validationError.left()
        val playlists = playlistRepository.findByUserId(userId)
        val updatedPlaylists = playlists.map {
            if (it.spotifyPlaylistId == playlistId) it.copy(type = type) else it
        }
        logger.info { "Updated type for playlist $playlistId (user ${userId.value}) to $type" }
        playlistRepository.saveAll(userId, updatedPlaylists)
        dashboardRefresh.notifyUserPlaylistMetadata(userId)
        return Unit.right()
    }

    private fun validatePlaylistTypeUpdate(userId: UserId, playlistId: String, type: PlaylistType): PlaylistSyncError? {
        userRepository.findById(userId) ?: run {
            logger.warn { "User not found for playlist type update: ${userId.value}" }
            return PlaylistSyncError.PLAYLIST_NOT_FOUND
        }
        val playlists = playlistRepository.findByUserId(userId)
        val playlist = playlists.find { it.spotifyPlaylistId == playlistId }
        return when {
            playlist == null -> {
                logger.warn { "Playlist $playlistId not found for user ${userId.value}" }
                PlaylistSyncError.PLAYLIST_NOT_FOUND
            }
            playlist.syncStatus != PlaylistSyncStatus.ACTIVE -> {
                logger.warn { "Playlist $playlistId is not active for user ${userId.value}, cannot set type" }
                PlaylistSyncError.PLAYLIST_NOT_ACTIVE
            }
            type == PlaylistType.ALL && playlists.any { it.type == PlaylistType.ALL && it.spotifyPlaylistId != playlistId } -> {
                logger.warn { "Playlist type ALL already assigned to another playlist for user ${userId.value}" }
                PlaylistSyncError.PLAYLIST_TYPE_CONFLICT
            }
            else -> null
        }
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

    companion object : KLogging() {
        private val YEAR_NAME_REGEX = Regex("\\d{4}")
    }
}
