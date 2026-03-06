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
import de.chrgroth.spotify.control.domain.port.out.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistDataRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyPlaylistPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyPlaylistTracksPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import kotlin.time.Clock

@ApplicationScoped
@Suppress("Unused")
class PlaylistSyncAdapter(
    private val userRepository: UserRepositoryPort,
    private val playlistRepository: PlaylistRepositoryPort,
    private val playlistDataRepository: PlaylistDataRepositoryPort,
    private val spotifyAccessToken: SpotifyAccessTokenPort,
    private val spotifyPlaylist: SpotifyPlaylistPort,
    private val spotifyPlaylistTracks: SpotifyPlaylistTracksPort,
    private val outboxPort: OutboxPort,
    private val dashboardRefresh: DashboardRefreshPort,
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
            // Re-read playlists after the Spotify API call to pick up any concurrent syncStatus changes
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
            // Enqueue SyncPlaylistData for active playlists that have a changed or missing snapshot
            updatedPlaylists
                .filter { it.syncStatus == PlaylistSyncStatus.ACTIVE }
                .filter { playlist ->
                    val existing = existingById[playlist.spotifyPlaylistId]
                    existing == null ||
                        existing.snapshotId != playlist.snapshotId ||
                        playlistDataRepository.findByUserIdAndPlaylistId(userId, playlist.spotifyPlaylistId) == null
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
        return spotifyPlaylistTracks.getPlaylistTracks(userId, accessToken, playlistId).map { playlist ->
            logger.info { "Synced ${playlist.tracks.size} track(s) for playlist $playlistId (user ${userId.value})" }
            playlistDataRepository.save(userId, playlist)
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
            playlistDataRepository.findByUserIdAndPlaylistId(userId, playlist.spotifyPlaylistId) == null
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
        playlists.find { it.spotifyPlaylistId == playlistId } ?: run {
            logger.warn { "Playlist $playlistId not found for user ${userId.value}" }
            return PlaylistSyncError.PLAYLIST_NOT_FOUND.left()
        }
        logger.info { "Enqueueing SyncPlaylistData for playlist $playlistId (user ${userId.value})" }
        outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(userId, playlistId))
        return Unit.right()
    }

    companion object : KLogging()
}
