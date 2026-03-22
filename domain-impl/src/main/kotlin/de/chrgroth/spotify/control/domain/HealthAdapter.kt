package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.HealthStats
import de.chrgroth.spotify.control.domain.model.PredicateStats
import de.chrgroth.spotify.control.domain.port.`in`.HealthPort
import de.chrgroth.spotify.control.domain.port.out.ConfigurationInfoPort
import de.chrgroth.spotify.control.domain.port.out.CronjobInfoPort
import de.chrgroth.spotify.control.domain.port.out.MongoStatsPort
import de.chrgroth.spotify.control.domain.port.out.OutboxManagementPort
import de.chrgroth.spotify.control.domain.port.out.OutgoingRequestStatsPort
import de.chrgroth.spotify.control.domain.port.out.PlaybackActivityPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class HealthAdapter(
    private val outboxManagement: OutboxManagementPort,
    private val outgoingRequestStats: OutgoingRequestStatsPort,
    private val mongoStats: MongoStatsPort,
    private val cronjobInfo: CronjobInfoPort,
    private val configurationInfo: ConfigurationInfoPort,
    private val playbackActivity: PlaybackActivityPort,
) : HealthPort {

    override fun getStats(): HealthStats = HealthStats(
        outgoingRequestStats = outgoingRequestStats.getRequestStats(),
        outboxPartitions = outboxManagement.getPartitionStats(),
        mongoCollectionStats = mongoStats.getCollectionStats(),
        mongoQueryStats = mongoStats.getQueryStats(),
        cronjobStats = cronjobInfo.getCronjobStats(),
        predicateStats = listOf(
            PredicateStats(name = "playbackActive", active = playbackActivity.isPlaybackActive(), lastCheck = playbackActivity.lastActivityTimestamp()),
        ),
        configurationStats = configurationInfo.getConfigurationStats(),
    )
}
