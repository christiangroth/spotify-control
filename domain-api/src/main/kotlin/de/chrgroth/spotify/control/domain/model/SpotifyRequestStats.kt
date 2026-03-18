package de.chrgroth.spotify.control.domain.model

data class OutgoingRequestStats(
    val endpoint: String,
    val requestCountLast24h: Long,
) {
    val requestCountLast24hFormatted: String get() = requestCountLast24h.formatted()
}
