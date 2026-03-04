package de.chrgroth.spotify.control.domain.model

data class HealthStats(
    val outgoingRequestStats: List<OutgoingRequestStats>,
    val outboxPartitions: List<OutboxPartitionStats>,
)
