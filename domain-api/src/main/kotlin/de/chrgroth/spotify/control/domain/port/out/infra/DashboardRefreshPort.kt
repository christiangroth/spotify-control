package de.chrgroth.spotify.control.domain.port.out.infra

interface DashboardRefreshPort {
  fun notifyUserPlaybackData()
  fun notifyUserPlaylistMetadata()
  fun notifyUserPlaylistChecks()
  fun notifyCatalogData()
}
