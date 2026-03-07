package de.chrgroth.spotify.control.domain.model

import kotlin.time.Instant

data class AppPlaybackItem(
    val userId: UserId,
    val playedAt: Instant,
    val trackId: String,
    val secondsPlayed: Long,
)
