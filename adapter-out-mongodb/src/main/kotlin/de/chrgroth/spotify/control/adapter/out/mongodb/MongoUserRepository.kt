package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
class UserRepositoryAdapter : UserRepositoryPort {

    override fun findById(spotifyUserId: UserId): User? =
        UserDocument.findById(spotifyUserId.value)?.toDomain()

    override fun findAll(): List<User> =
        UserDocument.listAll().map { it.toDomain() }

    override fun upsert(user: User) {
        val existing = UserDocument.findById(user.spotifyUserId.value)
        if (existing == null) {
            logger.info { "Creating new user: ${user.spotifyUserId.value}" }
        } else {
            logger.info { "Updating existing user: ${user.spotifyUserId.value}" }
        }
        val document = existing ?: UserDocument().apply {
            spotifyUserId = user.spotifyUserId.value
            createdAt = java.time.Instant.now()
        }
        document.displayName = user.displayName
        document.encryptedAccessToken = user.encryptedAccessToken
        document.encryptedRefreshToken = user.encryptedRefreshToken
        document.tokenExpiresAt = user.tokenExpiresAt.toJavaInstant()
        document.lastLoginAt = user.lastLoginAt.toJavaInstant()
        document.persistOrUpdate()
    }

    private fun UserDocument.toDomain() = User(
        spotifyUserId = UserId(spotifyUserId),
        displayName = displayName,
        encryptedAccessToken = encryptedAccessToken,
        encryptedRefreshToken = encryptedRefreshToken,
        tokenExpiresAt = tokenExpiresAt.toKotlinInstant(),
        lastLoginAt = lastLoginAt.toKotlinInstant(),
    )

    companion object : KLogging()
}
