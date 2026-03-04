package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.PlaylistSyncError
import de.chrgroth.spotify.control.domain.model.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.PlaylistSyncPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyPlaylistPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import kotlin.time.Clock

@ApplicationScoped
@Suppress("Unused")
class PlaylistSyncAdapter(
    private val userRepository: UserRepositoryPort,
    private val spotifyAccessToken: SpotifyAccessTokenPort,
    private val spotifyPlaylist: SpotifyPlaylistPort,
    private val outboxPort: OutboxPort,
) : PlaylistSyncPort {

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
            // Re-read user after the Spotify API call to pick up any concurrent syncStatus changes
            val currentUser = userRepository.findById(userId) ?: run {
                logger.warn { "User not found after Spotify API call for playlist sync: ${userId.value}" }
                return@map
            }
            val existingById = currentUser.playlists.associateBy { it.spotifyPlaylistId }
            val updatedPlaylists = spotifyPlaylists.map { item ->
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
            userRepository.upsert(currentUser.copy(playlists = updatedPlaylists))
        }
    }

    override fun updateSyncStatus(userId: UserId, playlistId: String, syncStatus: PlaylistSyncStatus): Either<DomainError, Unit> {
        val user = userRepository.findById(userId) ?: run {
            logger.warn { "User not found for playlist sync status update: ${userId.value}" }
            return PlaylistSyncError.PLAYLIST_NOT_FOUND.left()
        }
        val playlist = user.playlists.find { it.spotifyPlaylistId == playlistId } ?: run {
            logger.warn { "Playlist $playlistId not found for user ${userId.value}" }
            return PlaylistSyncError.PLAYLIST_NOT_FOUND.left()
        }
        val updatedPlaylists = user.playlists.map {
            if (it.spotifyPlaylistId == playlistId) it.copy(syncStatus = syncStatus) else it
        }
        logger.info { "Updated sync status for playlist $playlistId (user ${userId.value}) to $syncStatus" }
        userRepository.upsert(user.copy(playlists = updatedPlaylists))
        return Unit.right()
    }

    companion object : KLogging()
}
