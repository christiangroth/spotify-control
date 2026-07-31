package de.chrgroth.spotify.control.domain.port.out.playlist

interface PlaylistSyncNotificationPort {
  fun notifySyncFailed(reason: String)
}
