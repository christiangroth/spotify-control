package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.UserServicePort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@QuarkusTest
class UserServiceTests {

    @Inject
    lateinit var userService: UserServicePort

    @Test
    fun `first allowed user is accepted`() {
        assertThat(userService.isAllowed(UserId("test-user-a"))).isTrue()
    }

    @Test
    fun `second allowed user is accepted`() {
        assertThat(userService.isAllowed(UserId("test-user-b"))).isTrue()
    }

    @Test
    fun `unknown user is rejected`() {
        assertThat(userService.isAllowed(UserId("unknown-user"))).isFalse()
    }
}
