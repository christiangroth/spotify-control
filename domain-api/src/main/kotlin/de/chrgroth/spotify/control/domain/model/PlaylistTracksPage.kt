package de.chrgroth.spotify.control.domain.model

data class PlaylistTracksPage(
    val snapshotId: String,
    val tracks: List<PlaylistTrack>,
    val nextUrl: String?,
)
