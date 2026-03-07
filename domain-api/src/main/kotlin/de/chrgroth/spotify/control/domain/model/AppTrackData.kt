@file:Suppress("ForbiddenComment")

package de.chrgroth.spotify.control.domain.model

/**
 * Processed track metadata, shared across all users to avoid duplication in app_playback.
 * Fields not yet available from current data sources are nullable with TODO markers for
 * future population once additional Spotify API data (track, album, artist detail) is fetched.
 */
data class AppTrackData(
    val trackId: String,
    // TODO: populate albumId from Spotify track data when available
    val albumId: String? = null,
    val artistIds: List<String>,
    val trackTitle: String,
    // TODO: populate albumTitle from Spotify track data when available
    val albumTitle: String? = null,
    val artistNames: List<String>,
    // TODO: populate genres from Spotify artist data when available
    val genres: List<String> = emptyList(),
    // TODO: populate imageLink from Spotify album data when available
    val imageLink: String? = null,
)
