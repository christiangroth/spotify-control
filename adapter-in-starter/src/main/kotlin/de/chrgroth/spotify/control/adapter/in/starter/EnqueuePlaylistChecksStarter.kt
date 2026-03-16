package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.quarkus.starters.domain.Starter
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class EnqueuePlaylistChecksStarter(
    private val userRepository: UserRepositoryPort,
    private val playlistRepository: PlaylistRepositoryPort,
    private val outboxPort: OutboxPort,
) : Starter {

    override val id = "EnqueuePlaylistChecksStarter-v1"

    override fun execute() {
        val users = userRepository.findAll()
        logger.info { "Enqueuing playlist checks for ${users.size} user(s)" }
        users.forEach { user ->
            val userId = user.spotifyUserId
            runCatching {
                val playlists = playlistRepository.findByUserId(userId)
                val activePlaylists = playlists.filter { it.syncStatus == PlaylistSyncStatus.ACTIVE }
                logger.info { "Enqueuing playlist checks for ${activePlaylists.size} active playlist(s) of user ${userId.value}" }
                activePlaylists.forEach { playlist ->
                    outboxPort.enqueue(DomainOutboxEvent.RunPlaylistChecks(userId, playlist.spotifyPlaylistId))
                }
            }.onFailure { e ->
                logger.warn(e) { "Failed to enqueue playlist checks for user ${userId.value}: ${e.message}" }
            }
        }
    }

    companion object : KLogging()
}
