package de.chrgroth.spotify.control.domain.user

import arrow.core.Either
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.user.UserProfilePort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAuthPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class UserProfileService(
  private val userRepository: UserRepositoryPort,
  private val spotifyAccessToken: SpotifyAccessTokenPort,
  private val spotifyAuth: SpotifyAuthPort,
  private val outboxPort: OutboxPort,
) : UserProfilePort {

  // Single-user application: displayName only changes via the daily UpdateUserProfile job, so it
  // is cached the same way CurrentUserResolver caches the user id, and refreshed in update() when
  // it actually changes.
  @Volatile
  private var cachedDisplayName: String? = null

  override fun getDisplayName(): String? {
    val current = cachedDisplayName
    if (current != null) return current
    val resolved = userRepository.get()?.displayName
    if (resolved != null) cachedDisplayName = resolved
    return resolved
  }

  override fun enqueueUpdates() {
    userRepository.get() ?: return
    outboxPort.enqueue(DomainOutboxEvent.UpdateUserProfile())
  }

  override fun update(): Either<DomainError, Unit> {
    val user = userRepository.get() ?: return Unit.right()
    val accessToken = spotifyAccessToken.getValidAccessToken()
    return spotifyAuth.getUserProfile(accessToken).map { profile ->
      if (profile.displayName != user.displayName) {
        userRepository.upsert(user.copy(displayName = profile.displayName))
        cachedDisplayName = profile.displayName
      }
    }
  }

  override fun handle(event: DomainOutboxEvent.UpdateUserProfile): Either<DomainError, Unit> =
    update()

  companion object : KLogging()
}
