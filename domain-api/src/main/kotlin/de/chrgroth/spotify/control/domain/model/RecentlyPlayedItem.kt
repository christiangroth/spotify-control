package de.chrgroth.spotify.control.domain.model

import kotlin.time.Instant

data class RecentlyPlayedItem(
    val spotifyUserId: UserId,
    val trackId: String,
    val trackName: String,
    val artistNames: List<String>,
    val playedAt: Instant,
)
