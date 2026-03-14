package de.chrgroth.spotify.control.domain.model

import kotlin.time.Instant

data class AppPlaylistCheck(
    val checkId: String,
    val playlistId: String,
    val lastCheck: Instant,
    val succeeded: Boolean,
    val violations: List<String>,
)
