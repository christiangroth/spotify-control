package de.chrgroth.spotify.control.domain.model

/**
 * Transient data class holding Spotify album details fetched from the Spotify album API.
 * Used to enrich app_album with albumTitle, imageLink, genres, and artistId.
 */
data class SpotifyAlbumDetails(
    val albumId: String,
    val albumTitle: String?,
    val imageLink: String?,
    val genres: List<String>,
    val artistId: String?,
)
