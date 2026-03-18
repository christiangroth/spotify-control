package de.chrgroth.spotify.control.domain.model

data class OutboxEventTypeCount(
    val eventType: String,
    val count: Long,
) {
    val countFormatted: String get() = count.formatted()
}
