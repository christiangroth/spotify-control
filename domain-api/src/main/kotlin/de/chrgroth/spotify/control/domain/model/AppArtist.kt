@file:Suppress("ForbiddenComment")

package de.chrgroth.spotify.control.domain.model

import kotlin.time.Instant

/**
 * Processed artist metadata, shared across all users to avoid duplication in app_track.
 */
data class AppArtist(
    val artistId: String,
    val artistName: String,
    val genre: String? = null,
    val additionalGenres: List<String>? = null,
    val imageLink: String? = null,
    val type: String? = null,
    val lastSync: Instant? = null,
    val playbackProcessingStatus: ArtistPlaybackProcessingStatus = ArtistPlaybackProcessingStatus.UNDECIDED,
)
