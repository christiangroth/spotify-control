package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.UserId

interface DashboardRefreshPort {
    fun notifyUser(userId: UserId)
    fun notifyAllUsers()
}
