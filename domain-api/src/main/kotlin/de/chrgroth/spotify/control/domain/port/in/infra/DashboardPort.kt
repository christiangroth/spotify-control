package de.chrgroth.spotify.control.domain.port.`in`.infra

import de.chrgroth.spotify.control.domain.model.DashboardStats

interface DashboardPort {
  fun getStats(): DashboardStats
  fun getPlaybackStats(): DashboardStats
  fun getPlaylistMetadata(): DashboardStats
  fun getRecentlyPlayed(): DashboardStats
  fun getListeningStats(): DashboardStats
  fun getPlaylistCheckStats(): DashboardStats
  fun getCatalogStats(): DashboardStats
}
