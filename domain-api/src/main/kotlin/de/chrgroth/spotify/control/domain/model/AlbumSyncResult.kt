package de.chrgroth.spotify.control.domain.model

/**
 * Result of a Spotify album API call, containing the synced album and all its tracks.
 */
data class AlbumSyncResult(
    val album: AppAlbum,
    val tracks: List<AppTrack>,
)
