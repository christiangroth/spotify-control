package de.chrgroth.spotify.control.domain.port.out.playlist

import de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck

interface PlaylistCheckNotificationPort {
  fun notifyCheckPassed(check: AppPlaylistCheck, playlistName: String?)
  fun notifyViolationsChanged(check: AppPlaylistCheck, playlistName: String?, previousViolationCount: Int?)
}
