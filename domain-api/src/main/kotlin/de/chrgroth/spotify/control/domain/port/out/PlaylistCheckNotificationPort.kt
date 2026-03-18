package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.AppPlaylistCheck

interface PlaylistCheckNotificationPort {
    fun notifyCheckPassed(check: AppPlaylistCheck)
    fun notifyViolationsChanged(check: AppPlaylistCheck)
}
