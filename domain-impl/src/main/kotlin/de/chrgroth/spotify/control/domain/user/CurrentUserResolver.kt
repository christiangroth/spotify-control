package de.chrgroth.spotify.control.domain.user

import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped

// Single-user application: at most one stored user ever exists, and LoginService rejects login
// attempts from a second user once one is registered. The resolved id is therefore stable for the
// lifetime of this bean, so it is looked up once and cached rather than re-queried on every call.
@ApplicationScoped
class CurrentUserResolver(
  private val userRepository: UserRepositoryPort,
) {

  @Volatile
  private var cached: UserId? = null

  fun userId(): UserId? {
    val current = cached
    if (current != null) return current
    val resolved = userRepository.findAll().firstOrNull()?.spotifyUserId
    if (resolved != null) cached = resolved
    return resolved
  }
}
