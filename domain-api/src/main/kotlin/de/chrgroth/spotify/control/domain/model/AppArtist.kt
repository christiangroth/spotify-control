@file:Suppress("ForbiddenComment")

package de.chrgroth.spotify.control.domain.model

/**
 * Processed artist metadata, shared across all users to avoid duplication in app_track.
 * Genres are nullable pending additional Spotify API enrichment.
 */
data class AppArtist(
    val artistId: String,
    val artistName: String,
    // TODO: populate genres from Spotify artist data when available
    val genres: List<String> = emptyList(),
)
