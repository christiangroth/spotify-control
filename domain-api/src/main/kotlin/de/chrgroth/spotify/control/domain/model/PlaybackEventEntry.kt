package de.chrgroth.spotify.control.domain.model

import kotlin.time.Instant

data class PlaybackEventEntry(
    val type: String,
    val timestamp: Instant,
    val json: String,
    val isWarning: Boolean,
)
