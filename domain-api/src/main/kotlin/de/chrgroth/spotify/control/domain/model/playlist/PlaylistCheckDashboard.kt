package de.chrgroth.spotify.control.domain.model.playlist

data class PlaylistCheckDashboard(
  val displayName: String,
  val checks: List<AppPlaylistCheck>,
  val playlistNameById: Map<String, String>,
  val displayNames: Map<String, String>,
  val fixableCheckIds: Set<String>,
)
