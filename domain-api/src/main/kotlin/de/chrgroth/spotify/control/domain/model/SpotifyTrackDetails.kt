package de.chrgroth.spotify.control.domain.model

/**
 * Transient data class holding Spotify track details fetched from the Spotify track API.
 * Used to enrich app_track with albumId. Album details are fetched separately via EnrichAlbumDetails.
 */
data class SpotifyTrackDetails(
    val trackId: String,
    val albumId: String,
)
