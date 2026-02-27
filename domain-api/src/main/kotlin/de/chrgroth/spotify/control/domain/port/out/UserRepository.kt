package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.model.UserId

interface UserRepositoryPort {
    fun findById(spotifyUserId: UserId): User?
    fun findAll(): List<User>
    fun upsert(user: User)
}
