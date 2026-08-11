package de.chrgroth.spotify.control.domain.model.playlist

data class PlaylistSettingsEntry(
  val playlist: PlaylistInfo,
  val numberOfTracks: Int?,
  val numberOfArtists: Int?,
  val numberOfMissingArtists: Int?,
  val missingArtists: List<MissingArtist>,
)

data class PlaylistSettingsView(
  val entries: List<PlaylistSettingsEntry>,
)
