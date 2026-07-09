package de.chrgroth.spotify.control.domain.port.out.playlist

import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTrack

interface PlaylistRepositoryPort {
  fun findAll(): List<PlaylistInfo>
  fun replaceAll(playlists: List<PlaylistInfo>)
  fun findByPlaylistId(playlistId: String): Playlist?
  fun findTrackCounts(): Map<String, Int>
  fun save(playlist: Playlist)
  fun appendTracks(playlistId: String, tracks: List<PlaylistTrack>)
  fun updateLastSyncTime(playlistId: String, time: kotlin.time.Instant)
  fun setAllSyncInactive()
}
