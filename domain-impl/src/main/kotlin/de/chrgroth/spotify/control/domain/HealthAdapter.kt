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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

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

    override fun getStats(): HealthStats = runBlocking {
        val outgoingRequestStatsAsync = async(Dispatchers.IO) { outgoingRequestStats.getRequestStats() }
        val outboxPartitionsAsync = async(Dispatchers.IO) { outboxManagement.getPartitionStats() }
        val mongoCollectionStatsAsync = async(Dispatchers.IO) { mongoStats.getCollectionStats() }
        val mongoQueryStatsAsync = async(Dispatchers.IO) { mongoStats.getQueryStats() }
        val cronjobStatsAsync = async(Dispatchers.IO) { cronjobInfo.getCronjobStats() }
        val playbackActiveAsync = async(Dispatchers.IO) { playbackActivity.isPlaybackActive() }
        val lastActivityTimestampAsync = async(Dispatchers.IO) { playbackActivity.lastActivityTimestamp() }
        val configurationStatsAsync = async(Dispatchers.IO) { configurationInfo.getConfigurationStats() }
        HealthStats(
            outgoingRequestStats = outgoingRequestStatsAsync.await(),
            outboxPartitions = outboxPartitionsAsync.await(),
            mongoCollectionStats = mongoCollectionStatsAsync.await(),
            mongoQueryStats = mongoQueryStatsAsync.await(),
            cronjobStats = cronjobStatsAsync.await(),
            predicateStats = listOf(
                PredicateStats(name = "playbackActive", active = playbackActiveAsync.await(), lastCheck = lastActivityTimestampAsync.await()),
            ),
            configurationStats = configurationStatsAsync.await(),
        )
    }

    override fun activatePartition(partitionKey: String): Boolean =
        outboxManagement.activate(partitionKey)
}
