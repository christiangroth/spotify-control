package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.UserId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UserServiceAdapterTests {

    private val adapter = UserServiceAdapter(listOf("test-user-a", "test-user-b"))

    @Test
    fun `first allowed user is accepted`() {
        assertThat(adapter.isAllowed(UserId("test-user-a"))).isTrue()
    }

    @Test
    fun `second allowed user is accepted`() {
        assertThat(adapter.isAllowed(UserId("test-user-b"))).isTrue()
    }

    @Test
    fun `unknown user is rejected`() {
        assertThat(adapter.isAllowed(UserId("unknown-user"))).isFalse()
    }
}
