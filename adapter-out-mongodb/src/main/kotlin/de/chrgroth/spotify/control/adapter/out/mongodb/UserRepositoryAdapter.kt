package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
class UserRepositoryAdapter : UserRepositoryPort {

    @Inject
    lateinit var userDocumentRepository: UserDocumentRepository

    @Inject
    lateinit var mongoQueryMetrics: MongoQueryMetrics

    override fun findById(spotifyUserId: UserId): User? =
        mongoQueryMetrics.timed("app_user.findById") {
            userDocumentRepository.findById(spotifyUserId.value)?.toDomain()
        }

    override fun findAll(): List<User> =
        mongoQueryMetrics.timed("app_user.findAll") {
            userDocumentRepository.listAll().map { it.toDomain() }
        }

    override fun upsert(user: User) {
        val existing = mongoQueryMetrics.timed("app_user.upsert.findById") {
            userDocumentRepository.findById(user.spotifyUserId.value)
        }
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
        mongoQueryMetrics.timed("app_user.upsert.persistOrUpdate") {
            userDocumentRepository.persistOrUpdate(document)
        }
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

