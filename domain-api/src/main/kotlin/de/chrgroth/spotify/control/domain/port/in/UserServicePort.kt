package de.chrgroth.spotify.control.domain.port.`in`

import de.chrgroth.spotify.control.domain.model.UserId

interface UserServicePort {
    fun isAllowed(userId: UserId): Boolean
}
