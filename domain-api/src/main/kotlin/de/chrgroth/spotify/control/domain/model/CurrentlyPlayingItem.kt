package de.chrgroth.spotify.control.domain.model

import kotlin.time.Instant

data class CurrentlyPlayingItem(
    val spotifyUserId: UserId,
    val trackId: String,
    val trackName: String,
    val artistIds: List<String>,
    val artistNames: List<String>,
    val progressMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val observedAt: Instant,
)
