package de.chrgroth.spotify.control.domain.model

data class OutgoingRequestStats(
    val host: String,
    val requestCountLast24h: Long,
)
