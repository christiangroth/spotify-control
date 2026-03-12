package de.chrgroth.spotify.control.domain.model

import kotlin.time.Instant

/**
 * Processed album metadata, shared across all users to avoid duplication in app_track.
 * All fields are populated by EnrichTrackDetails from the album object embedded in the
 * Spotify track API response. The embedded album object includes images, release date,
 * album type, total tracks, and artist information.
 */
data class AppAlbum(
    val albumId: String,
    val albumType: String? = null,
    val totalTracks: Int? = null,
    val albumTitle: String? = null,
    val imageLink: String? = null,
    val releaseDate: String? = null,
    val releaseDatePrecision: String? = null,
    val type: String? = null,
    val artistId: String? = null,
    val artistName: String? = null,
    val additionalArtistIds: List<String>? = null,
    val additionalArtistNames: List<String>? = null,
    val lastEnrichmentDate: Instant? = null,
)
