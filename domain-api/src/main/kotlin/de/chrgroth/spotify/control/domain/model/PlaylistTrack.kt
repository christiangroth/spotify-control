package de.chrgroth.spotify.control.domain.model

data class PlaylistTrack(
    val trackId: String,
    val trackName: String,
    val artistIds: List<String>,
    val artistNames: List<String>,
)
