package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.outbox.OutboxRepository
import de.chrgroth.spotify.control.util.starters.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class MigrateToSpotifyPlaybackPartitionStarter(private val outboxRepository: OutboxRepository) : Starter {

    override val id = "MigrateToSpotifyPlaybackPartitionStarter-v1"

    override fun execute() {
        val fromKeys = listOf("to-spotify-recently-played", "to-spotify-currently-playing")
        val toPartition = DomainOutboxPartition.ToSpotifyPlayback
        fromKeys.forEach { fromKey ->
            val migrated = outboxRepository.migratePartition(fromKey, toPartition)
            if (migrated > 0) {
                logger.info { "Migrated $migrated outbox tasks from partition '$fromKey' to '${toPartition.key}'" }
            }
        }
    }

    companion object : KLogging()
}
