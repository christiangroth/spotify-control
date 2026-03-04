package de.chrgroth.spotify.control.domain.model

data class Playlist(
    val spotifyPlaylistId: String,
    val snapshotId: String,
    val tracks: List<PlaylistTrack>,
)
