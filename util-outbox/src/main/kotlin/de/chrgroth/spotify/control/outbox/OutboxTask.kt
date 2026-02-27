package de.chrgroth.spotify.control.outbox

import kotlin.time.Instant

data class OutboxTask(
    val id: String,
    val partition: String,
    val eventType: String,
    val payload: String,
    val status: OutboxTaskStatus,
    val attempts: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val nextRetryAt: Instant?,
    val lastError: String?,
)
