package de.chrgroth.spotify.control.util.outbox

import java.time.Instant

data class OutboxPartitionInfo(
    val key: String,
    val status: String,
    val statusReason: String?,
    val pausedUntil: Instant?,
)
