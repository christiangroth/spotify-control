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
  private val currentUserResolver: CurrentUserResolver,
  private val spotifyAccessToken: SpotifyAccessTokenPort,
  private val spotifyAuth: SpotifyAuthPort,
  private val outboxPort: OutboxPort,
) : UserProfilePort {

  override fun getDisplayName(): String? = currentUserResolver.userId()?.let { userRepository.findById(it)?.displayName }

  override fun enqueueUpdates() {
    currentUserResolver.userId() ?: return
    outboxPort.enqueue(DomainOutboxEvent.UpdateUserProfile())
  }

  override fun update(): Either<DomainError, Unit> {
    val userId = currentUserResolver.userId() ?: return Unit.right()
    val user = userRepository.findById(userId) ?: run {
      logger.warn { "User not found for profile update: ${userId.value}" }
      return Unit.right()
    }
    val accessToken = spotifyAccessToken.getValidAccessToken()
    return spotifyAuth.getUserProfile(accessToken).map { profile ->
      if (profile.displayName != user.displayName) {
        userRepository.upsert(user.copy(displayName = profile.displayName))
      }
    }
  }

  override fun handle(event: DomainOutboxEvent.UpdateUserProfile): Either<DomainError, Unit> =
    update()

  companion object : KLogging()
}
