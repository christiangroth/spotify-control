package de.chrgroth.spotify.control.domain.model

import kotlin.time.Instant

data class CurrentlyPlayingItem(
    val spotifyUserId: UserId,
    val trackId: TrackId,
    val trackName: String,
    val artistIds: List<ArtistId>,
    val artistNames: List<String>,
    val progressMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val observedAt: Instant,
    val albumId: AlbumId? = null,
)
