package de.chrgroth.spotify.control.domain.model

import kotlin.time.Instant

data class PlaylistInfo(
    val spotifyPlaylistId: String,
    val snapshotId: String,
    val lastSnapshotIdSyncTime: Instant,
    val name: String,
    val syncStatus: PlaylistSyncStatus,
)
