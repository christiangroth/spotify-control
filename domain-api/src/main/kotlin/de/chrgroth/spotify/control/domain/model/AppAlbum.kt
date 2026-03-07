@file:Suppress("ForbiddenComment")

package de.chrgroth.spotify.control.domain.model

/**
 * Processed album metadata, shared across all users to avoid duplication in app_track.
 * albumTitle and imageLink are nullable pending additional Spotify API enrichment.
 */
data class AppAlbum(
    val albumId: String,
    // TODO: populate albumTitle from Spotify album data when available
    val albumTitle: String? = null,
    // TODO: populate imageLink from Spotify album data when available
    val imageLink: String? = null,
)
