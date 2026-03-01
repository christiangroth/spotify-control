package de.chrgroth.spotify.control.domain.port.`in`

import de.chrgroth.spotify.control.domain.model.UserId

interface RecentlyPlayedPort {
    fun enqueueUpdates()
    fun update(userId: UserId)
}
