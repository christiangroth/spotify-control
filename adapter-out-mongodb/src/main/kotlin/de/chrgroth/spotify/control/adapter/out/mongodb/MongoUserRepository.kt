package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.port.out.UserRepository
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

@ApplicationScoped
class MongoUserRepository : UserRepository {

    override fun findById(spotifyUserId: String): User? =
        UserDocument.findById(spotifyUserId)?.toDomain()

    override fun upsert(user: User) {
        val existing = UserDocument.findById(user.spotifyUserId)
        val document = existing ?: UserDocument().apply {
            spotifyUserId = user.spotifyUserId
            createdAt = user.createdAt.toJavaInstant()
        }
        document.displayName = user.displayName
        document.encryptedAccessToken = user.encryptedAccessToken
        document.encryptedRefreshToken = user.encryptedRefreshToken
        document.tokenExpiresAt = user.tokenExpiresAt.toJavaInstant()
        document.lastLoginAt = user.lastLoginAt.toJavaInstant()
        document.persistOrUpdate()
    }

    private fun UserDocument.toDomain() = User(
        spotifyUserId = spotifyUserId,
        displayName = displayName,
        encryptedAccessToken = encryptedAccessToken,
        encryptedRefreshToken = encryptedRefreshToken,
        tokenExpiresAt = tokenExpiresAt.toKotlinInstant(),
        createdAt = createdAt.toKotlinInstant(),
        lastLoginAt = lastLoginAt.toKotlinInstant(),
    )
}
