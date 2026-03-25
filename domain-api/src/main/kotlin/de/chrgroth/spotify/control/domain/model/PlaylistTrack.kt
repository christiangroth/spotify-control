package de.chrgroth.spotify.control.domain.model

data class PlaylistTrack(
    val trackId: String,
    val artistIds: List<String>,
    val albumId: String,
)
