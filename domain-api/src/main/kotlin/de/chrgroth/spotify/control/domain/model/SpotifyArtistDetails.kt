package de.chrgroth.spotify.control.domain.model

/**
 * Transient data class holding Spotify artist details fetched from the Spotify artist API.
 * Used to enrich app_artist with genres and imageLink.
 */
data class SpotifyArtistDetails(
    val artistId: String,
    val name: String,
    val genres: List<String>,
    val imageLink: String? = null,
)
