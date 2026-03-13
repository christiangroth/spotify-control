package de.chrgroth.spotify.control.domain.model

/**
 * Result of a Spotify track details API call, containing the enriched track and the album
 * extracted from the album object embedded in the track response.
 */
data class TrackSyncResult(
    val track: AppTrack,
    val album: AppAlbum,
)
