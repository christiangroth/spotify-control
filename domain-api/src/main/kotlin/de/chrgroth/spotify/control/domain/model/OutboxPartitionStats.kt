package de.chrgroth.spotify.control.domain.model

import de.chrgroth.spotify.control.domain.util.formatted
import kotlin.time.Instant

data class OutboxPartitionStats(
    val name: String,
    val status: String,
    val documentCount: Long,
    val blockedUntil: Instant?,
    val eventTypeCounts: List<OutboxEventTypeCount> = emptyList(),
) {
    val documentCountFormatted: String get() = documentCount.formatted()
}
