package de.chrgroth.spotify.control.domain.model

import kotlin.time.Instant

data class RecentlyPartialPlayedItem(
    val spotifyUserId: UserId,
    val trackId: TrackId,
    val trackName: String,
    val artistIds: List<ArtistId>,
    val artistNames: List<String>,
    val playedAt: Instant,
    val playedSeconds: Long,
    val albumId: AlbumId? = null,
)
