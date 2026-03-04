package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.HealthStats
import de.chrgroth.spotify.control.domain.port.`in`.HealthStatsPort
import de.chrgroth.spotify.control.domain.port.out.MongoStatsPort
import de.chrgroth.spotify.control.domain.port.out.OutboxInfoPort
import de.chrgroth.spotify.control.domain.port.out.OutgoingRequestStatsPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class HealthStatsAdapter(
    private val outboxInfo: OutboxInfoPort,
    private val outgoingRequestStats: OutgoingRequestStatsPort,
    private val mongoStats: MongoStatsPort,
) : HealthStatsPort {

    override fun getStats(): HealthStats = HealthStats(
        outgoingRequestStats = outgoingRequestStats.getRequestStats(),
        outboxPartitions = outboxInfo.getPartitionStats(),
        mongoCollectionStats = mongoStats.getCollectionStats(),
        mongoQueryStats = mongoStats.getQueryStats(),
    )
}
