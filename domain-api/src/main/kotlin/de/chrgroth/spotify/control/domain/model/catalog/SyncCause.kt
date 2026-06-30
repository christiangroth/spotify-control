package de.chrgroth.spotify.control.domain.model.catalog

sealed interface SyncCause {
  data class Playback(val trackId: String) : SyncCause
  data class Playlist(val playlistId: String, val trackId: String) : SyncCause
  data class ArtistDiscography(val artistId: String) : SyncCause
  data object ManualResync : SyncCause
}
