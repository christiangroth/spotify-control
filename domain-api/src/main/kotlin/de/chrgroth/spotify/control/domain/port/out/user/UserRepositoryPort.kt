package de.chrgroth.spotify.control.domain.port.out.user

import de.chrgroth.spotify.control.domain.model.user.User

interface UserRepositoryPort {
  fun get(): User?
  fun upsert(user: User)
}
