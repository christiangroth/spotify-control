package de.chrgroth.spotify.control.domain.model

data class SpotifyRequestStats(
    val host: String,
    val requestCountLast24h: Long,
)
