package de.chrgroth.spotify.control.domain.model

data class OutboxPartitionStats(
    val name: String,
    val status: String,
    val documentCount: Long,
)
