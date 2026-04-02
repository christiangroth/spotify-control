package de.chrgroth.spotify.control.domain.playlist.check

import de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.user.UserId

interface PlaylistCheckRunner {
  val checkId: String
  val displayName: String
  fun isApplicable(playlistInfo: PlaylistInfo?): Boolean = true
  fun run(userId: UserId, playlistId: String, playlist: Playlist, currentPlaylistInfo: PlaylistInfo?, allPlaylistInfos: List<PlaylistInfo>): AppPlaylistCheck
}
