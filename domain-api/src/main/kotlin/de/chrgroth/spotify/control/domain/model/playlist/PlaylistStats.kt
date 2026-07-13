package de.chrgroth.spotify.control.domain.model.playlist

data class PlaylistStats(
  val trackedCount: Int = 0,
  val outOfSyncCount: Int = 0,
  val pendingAlbumUpgradeCount: Int = 0,
)
