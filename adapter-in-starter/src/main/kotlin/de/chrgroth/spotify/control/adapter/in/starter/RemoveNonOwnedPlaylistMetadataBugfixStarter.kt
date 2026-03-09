package de.chrgroth.spotify.control.adapter.`in`.starter

import arrow.core.Either
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyPlaylistPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import de.chrgroth.quarkus.starters.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class RemoveNonOwnedPlaylistMetadataBugfixStarter(
    private val userRepository: UserRepositoryPort,
    private val spotifyAccessToken: SpotifyAccessTokenPort,
    private val spotifyPlaylist: SpotifyPlaylistPort,
    private val playlistRepository: PlaylistRepositoryPort,
) : Starter {

    override val id = "RemoveNonOwnedPlaylistMetadata-v1"

    override fun execute() {
        val users = userRepository.findAll()
        logger.info { "Checking ${users.size} user(s) for non-owned playlist metadata" }
        users.forEach { user ->
            val userId = user.spotifyUserId
            runCatching {
                val accessToken = spotifyAccessToken.getValidAccessToken(userId)
                when (val result = spotifyPlaylist.getPlaylists(userId, accessToken)) {
                    is Either.Left -> logger.warn { "Failed to get playlists from Spotify for user ${userId.value}: ${result.value}" }
                    is Either.Right -> {
                        val ownedPlaylistIds = result.value
                            .filter { it.ownerId == userId.value }
                            .map { it.id }
                            .toSet()
                        val existing = playlistRepository.findByUserId(userId)
                        val nonOwned = existing.filter { it.spotifyPlaylistId !in ownedPlaylistIds }
                        if (nonOwned.isNotEmpty()) {
                            logger.info { "Removing ${nonOwned.size} non-owned playlist metadata document(s) for user ${userId.value}" }
                            val owned = existing.filter { it.spotifyPlaylistId in ownedPlaylistIds }
                            playlistRepository.saveAll(userId, owned)
                        } else {
                            logger.info { "No non-owned playlist metadata found for user ${userId.value}" }
                        }
                    }
                }
            }.onFailure { e ->
                logger.warn(e) { "Failed to process user ${userId.value}: ${e.message}" }
            }
        }
    }

    companion object : KLogging()
}
