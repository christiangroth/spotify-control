package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.UserId

interface DashboardRefreshPort {
    fun notifyUserPlaybackData(userId: UserId)
    fun notifyUserPlaylistMetadata(userId: UserId)
    fun notifyUserPlaylistChecks(userId: UserId)
    fun notifyCatalogStats()
}
