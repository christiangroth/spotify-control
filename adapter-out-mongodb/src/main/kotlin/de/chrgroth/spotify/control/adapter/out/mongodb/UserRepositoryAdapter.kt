package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import de.chrgroth.spotify.control.domain.model.user.User
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

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
        val now = java.time.Instant.now()
        mongoQueryMetrics.timed("app_user.upsert") {
            userDocumentRepository.mongoCollection().updateOne(
                Filters.eq("_id", user.spotifyUserId.value),
                Updates.combine(
                    Updates.set("displayName", user.displayName),
                    Updates.set("encryptedAccessToken", user.encryptedAccessToken),
                    Updates.set("encryptedRefreshToken", user.encryptedRefreshToken),
                    Updates.set("tokenExpiresAt", user.tokenExpiresAt.toJavaInstant()),
                    Updates.set("lastLoginAt", user.lastLoginAt.toJavaInstant()),
                    Updates.setOnInsert("createdAt", now),
                ),
                UpdateOptions().upsert(true),
            )
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
}
