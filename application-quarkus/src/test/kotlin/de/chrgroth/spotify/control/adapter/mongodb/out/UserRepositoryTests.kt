package de.chrgroth.spotify.control.adapter.mongodb.out

import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.port.out.UserRepository
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

@QuarkusTest
class UserRepositoryTests {

    @Inject
    lateinit var userRepository: UserRepository

    private val userId = "test-user-123"

    @BeforeEach
    fun cleanup() {
        de.chrgroth.spotify.control.adapter.out.mongodb.UserDocument.deleteAll()
    }

    @Test
    fun `findById returns null when user does not exist`() {
        assertThat(userRepository.findById(userId)).isNull()
    }

    @Test
    fun `upsert creates user on first login and findById retrieves it`() {
        val now = Clock.System.now().let { Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) }
        val user = User(
            spotifyUserId = userId,
            displayName = "Test User",
            encryptedAccessToken = "encrypted-access",
            encryptedRefreshToken = "encrypted-refresh",
            tokenExpiresAt = now + 1.hours,
            createdAt = now,
            lastLoginAt = now,
        )

        userRepository.upsert(user)

        val found = userRepository.findById(userId)
        assertThat(found).isNotNull()
        assertThat(found!!.spotifyUserId).isEqualTo(userId)
        assertThat(found.displayName).isEqualTo("Test User")
        assertThat(found.encryptedAccessToken).isEqualTo("encrypted-access")
        assertThat(found.encryptedRefreshToken).isEqualTo("encrypted-refresh")
    }

    @Test
    fun `upsert updates tokens on subsequent login but preserves createdAt`() {
        val now = Clock.System.now().let { Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) }
        val firstLogin = User(
            spotifyUserId = userId,
            displayName = "Test User",
            encryptedAccessToken = "first-access",
            encryptedRefreshToken = "first-refresh",
            tokenExpiresAt = now + 1.hours,
            createdAt = now,
            lastLoginAt = now,
        )
        userRepository.upsert(firstLogin)

        val laterLogin = now + 2.hours
        val secondLogin = firstLogin.copy(
            encryptedAccessToken = "second-access",
            encryptedRefreshToken = "second-refresh",
            tokenExpiresAt = laterLogin + 1.hours,
            lastLoginAt = laterLogin,
        )
        userRepository.upsert(secondLogin)

        val found = userRepository.findById(userId)!!
        assertThat(found.encryptedAccessToken).isEqualTo("second-access")
        assertThat(found.encryptedRefreshToken).isEqualTo("second-refresh")
        assertThat(found.createdAt).isEqualTo(now)
        assertThat(found.lastLoginAt).isEqualTo(laterLogin)
    }
}
