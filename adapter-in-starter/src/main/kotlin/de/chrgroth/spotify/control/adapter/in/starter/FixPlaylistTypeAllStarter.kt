package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.quarkus.starters.domain.Starter
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class FixPlaylistTypeAllStarter(
    private val userRepository: UserRepositoryPort,
    private val playlistRepository: PlaylistRepositoryPort,
) : Starter {

    override val id = "FixPlaylistTypeAllStarter-v1"

    override fun execute() {
        val users = userRepository.findAll()
        logger.info { "Fixing playlist type ALL for ${users.size} user(s)" }
        users.forEach { user ->
            val userId = user.spotifyUserId
            runCatching {
                val playlists = playlistRepository.findByUserId(userId)
                val updated = playlists.map { playlist ->
                    if (playlist.syncStatus == PlaylistSyncStatus.ACTIVE &&
                        playlist.name.equals("all", ignoreCase = true) &&
                        playlist.type != PlaylistType.ALL
                    ) {
                        logger.info { "Fixing type ALL for playlist '${playlist.name}' (${playlist.spotifyPlaylistId}) for user ${userId.value}" }
                        playlist.copy(type = PlaylistType.ALL)
                    } else {
                        playlist
                    }
                }
                val changed = updated.count { new ->
                    playlists.any { old -> old.spotifyPlaylistId == new.spotifyPlaylistId && old.type != new.type }
                }
                if (changed > 0) {
                    logger.info { "Saving $changed updated playlist(s) for user ${userId.value}" }
                    playlistRepository.saveAll(userId, updated)
                } else {
                    logger.info { "No playlist type ALL changes needed for user ${userId.value}" }
                }
            }.onFailure { e ->
                logger.warn(e) { "Failed to fix playlist type ALL for user ${userId.value}: ${e.message}" }
            }
        }
    }

    companion object : KLogging()
}
