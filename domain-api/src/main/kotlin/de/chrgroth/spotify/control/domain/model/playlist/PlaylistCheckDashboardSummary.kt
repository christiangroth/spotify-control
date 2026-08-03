package de.chrgroth.spotify.control.domain.model.playlist

data class PlaylistCheckDashboardSummary(
  val displayName: String,
  val checks: List<AppPlaylistCheck>,
  val playlistNameById: Map<String, String>,
)
