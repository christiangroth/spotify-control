package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.User

interface UserRepository {
    fun findById(spotifyUserId: String): User?
    fun upsert(user: User)
}
