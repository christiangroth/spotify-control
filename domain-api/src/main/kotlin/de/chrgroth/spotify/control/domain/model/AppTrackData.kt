package de.chrgroth.spotify.control.domain.model

import kotlin.time.Instant

/**
 * Processed track metadata, shared across all users to avoid duplication in app_playback.
 * References to app_artist and app_album by ID for further deduplication.
 * Enrichment fields (artistName, additionalArtistNames, albumId, albumName, discNumber,
 * durationMs, trackNumber, type) are populated by EnrichTrackDetails.
 */
data class AppTrack(
    val trackId: String,
    val trackTitle: String,
    val albumId: String? = null,
    val albumName: String? = null,
    val artistId: String,
    val artistName: String? = null,
    val additionalArtistIds: List<String> = emptyList(),
    val additionalArtistNames: List<String>? = null,
    val discNumber: Int? = null,
    val durationMs: Long? = null,
    val trackNumber: Int? = null,
    val type: String? = null,
    val lastEnrichmentDate: Instant? = null,
)
