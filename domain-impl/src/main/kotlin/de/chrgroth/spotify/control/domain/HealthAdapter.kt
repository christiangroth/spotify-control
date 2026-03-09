package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.HealthStats
import de.chrgroth.spotify.control.domain.port.`in`.HealthPort
import de.chrgroth.spotify.control.domain.port.out.CronjobInfoPort
import de.chrgroth.spotify.control.domain.port.out.MongoStatsPort
import de.chrgroth.spotify.control.domain.port.out.OutboxManagementPort
import de.chrgroth.spotify.control.domain.port.out.OutgoingRequestStatsPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class HealthAdapter(
    private val outboxManagement: OutboxManagementPort,
    private val outgoingRequestStats: OutgoingRequestStatsPort,
    private val mongoStats: MongoStatsPort,
    private val cronjobInfo: CronjobInfoPort,
) : HealthPort {

    override fun getStats(): HealthStats = HealthStats(
        outgoingRequestStats = outgoingRequestStats.getRequestStats(),
        outboxPartitions = outboxManagement.getPartitionStats(),
        mongoCollectionStats = mongoStats.getCollectionStats(),
        mongoQueryStats = mongoStats.getQueryStats(),
        cronjobStats = cronjobInfo.getCronjobStats(),
    )

    override fun activatePartition(partitionKey: String): Boolean =
        outboxManagement.activate(partitionKey)
}
