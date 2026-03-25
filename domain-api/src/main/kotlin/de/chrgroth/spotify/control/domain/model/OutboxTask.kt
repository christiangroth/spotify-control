package de.chrgroth.spotify.control.domain.model

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class OutboxTask(
    val eventType: String,
    val deduplicationKey: String,
    val priority: String,
    val status: String,
    val attempts: Int,
    val nextRetryAt: Instant?,
    val createdAt: Instant,
    val lastError: String?,
) {
    val priorityOrder: Int get() = if (priority == "HIGH") 0 else 1
    val nextRetryAtFormatted: String get() = nextRetryAt?.let { FORMATTER.format(it) } ?: "-"
    val createdAtFormatted: String get() = FORMATTER.format(createdAt)
    val isHighPriority: Boolean get() = priority == "HIGH"

    companion object {
        private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
    }
}
