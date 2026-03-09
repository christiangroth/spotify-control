package de.chrgroth.spotify.control.domain.port.`in`

import de.chrgroth.spotify.control.domain.model.DashboardStats
import de.chrgroth.spotify.control.domain.model.UserId

interface DashboardPort {
    fun getStats(userId: UserId): DashboardStats
}
