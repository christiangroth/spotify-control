package de.chrgroth.spotify.control.domain.model

data class PlaylistTrack(
    val trackId: TrackId,
    val artistIds: List<ArtistId>,
    val albumId: AlbumId,
)
