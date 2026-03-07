@file:Suppress("ForbiddenComment")

package de.chrgroth.spotify.control.domain.model

/**
 * Processed album metadata, shared across all users to avoid duplication in app_track.
 * albumTitle, imageLink, genres, and artistId are populated by EnrichAlbumDetails.
 */
data class AppAlbum(
    val albumId: String,
    val albumTitle: String? = null,
    val imageLink: String? = null,
    val genres: List<String> = emptyList(),
    val artistId: String? = null,
)
