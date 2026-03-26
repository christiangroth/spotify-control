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
class ApplyPlaylistTypeStarter(
    private val userRepository: UserRepositoryPort,
    private val playlistRepository: PlaylistRepositoryPort,
) : Starter {

    override val id = "ApplyPlaylistTypeStarter-v1"

    override fun execute() {
        val users = userRepository.findAll()
        logger.info { "Applying playlist types for ${users.size} user(s)" }
        users.forEach { user ->
            val userId = user.spotifyUserId
            runCatching {
                val playlists = playlistRepository.findByUserId(userId)
                val updated = playlists.map { playlist ->
                    if (playlist.syncStatus != PlaylistSyncStatus.ACTIVE || playlist.type != null) {
                        playlist
                    } else {
                        val type = when {
                            playlist.name == "All" -> PlaylistType.ALL
                            playlist.name.matches(YEAR_NAME_REGEX) -> PlaylistType.YEAR
                            else -> PlaylistType.UNKNOWN
                        }
                        logger.info { "Setting type $type for active playlist '${playlist.name}' (${playlist.spotifyPlaylistId}) for user ${userId.value}" }
                        playlist.copy(type = type)
                    }
                }
                val changed = updated.count { new -> playlists.any { old -> old.spotifyPlaylistId == new.spotifyPlaylistId && old.type != new.type } }
                if (changed > 0) {
                    logger.info { "Saving $changed updated playlist(s) for user ${userId.value}" }
                    playlistRepository.saveAll(userId, updated)
                } else {
                    logger.info { "No playlist type changes needed for user ${userId.value}" }
                }
            }.onFailure { e ->
                logger.warn(e) { "Failed to apply playlist types for user ${userId.value}: ${e.message}" }
            }
        }
    }

    companion object : KLogging() {
        private val YEAR_NAME_REGEX = Regex("\\d{4}")
    }
}
