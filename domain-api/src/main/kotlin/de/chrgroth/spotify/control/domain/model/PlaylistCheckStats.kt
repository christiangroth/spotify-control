package de.chrgroth.spotify.control.domain.model

data class PlaylistCheckStats(
    val succeededChecks: Long,
    val totalChecks: Long,
    val allSucceeded: Boolean,
)
