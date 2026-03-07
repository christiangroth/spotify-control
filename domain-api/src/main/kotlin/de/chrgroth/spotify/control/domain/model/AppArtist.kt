@file:Suppress("ForbiddenComment")

package de.chrgroth.spotify.control.domain.model

/**
 * Processed artist metadata, shared across all users to avoid duplication in app_track.
 */
data class AppArtist(
    val artistId: String,
    val artistName: String,
    val genres: List<String> = emptyList(),
    val imageLink: String? = null,
)
