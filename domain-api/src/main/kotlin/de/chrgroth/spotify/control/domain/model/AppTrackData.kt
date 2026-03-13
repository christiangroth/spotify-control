package de.chrgroth.spotify.control.domain.model

import kotlin.time.Instant

/**
 * Processed track metadata, shared across all users to avoid duplication in app_playback.
 * References to app_artist and app_album by ID for further deduplication.
 * Sync fields (artistName, additionalArtistNames, albumId, albumName, discNumber,
 * durationMs, trackNumber, type) are populated by SyncMissingTracks.
 */
data class AppTrack(
    val id: TrackId,
    val title: String,
    val albumId: AlbumId? = null,
    val albumName: String? = null,
    val artistId: ArtistId,
    val artistName: String? = null,
    val additionalArtistIds: List<ArtistId> = emptyList(),
    val additionalArtistNames: List<String>? = null,
    val discNumber: Int? = null,
    val durationMs: Long? = null,
    val trackNumber: Int? = null,
    val type: String? = null,
    val lastSync: Instant? = null,
)
