@file:Suppress("ForbiddenComment")

package de.chrgroth.spotify.control.domain.model

/**
 * Processed track metadata, shared across all users to avoid duplication in app_playback.
 * References to app_artist and app_album by ID for further deduplication.
 * albumId is populated by EnrichTrackDetails.
 */
data class AppTrack(
    val trackId: String,
    val trackTitle: String,
    val albumId: String? = null,
    val artistId: String,
    val additionalArtistIds: List<String> = emptyList(),
)
