package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.RefreshToken
import de.chrgroth.spotify.control.domain.model.SpotifyProfile
import de.chrgroth.spotify.control.domain.model.SpotifyProfileId
import de.chrgroth.spotify.control.domain.model.SpotifyTokens
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.LoginResult
import de.chrgroth.spotify.control.domain.port.`in`.UserServicePort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAuthPort
import de.chrgroth.spotify.control.domain.port.out.TokenEncryptionPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LoginServiceAdapterTests {

    private val spotifyAuth: SpotifyAuthPort = mockk()
    private val userService: UserServicePort = mockk()
    private val userRepository: UserRepositoryPort = mockk()
    private val tokenEncryption: TokenEncryptionPort = mockk()

    private val adapter = LoginServiceAdapter(spotifyAuth, userService, userRepository, tokenEncryption)

    private val tokens = SpotifyTokens(AccessToken("access"), RefreshToken("refresh"), 3600)
    private val profile = SpotifyProfile(SpotifyProfileId("user-1"), "Test User")

    @Test
    fun `allowed user succeeds and user is upserted`() {
        every { spotifyAuth.exchangeCode("code") } returns tokens
        every { spotifyAuth.getUserProfile("access") } returns profile
        every { userService.isAllowed(UserId("user-1")) } returns true
        every { tokenEncryption.encrypt(any()) } returns "encrypted"
        every { userRepository.upsert(any()) } just runs

        val result = adapter.handleCallback("code")

        assertThat(result).isInstanceOf(LoginResult.Success::class.java)
        assertThat((result as LoginResult.Success).userId).isEqualTo(UserId("user-1"))
        verify { userRepository.upsert(any()) }
    }

    @Test
    fun `not-allowed user returns failure and user is not upserted`() {
        every { spotifyAuth.exchangeCode("code") } returns tokens
        every { spotifyAuth.getUserProfile("access") } returns profile
        every { userService.isAllowed(UserId("user-1")) } returns false

        val result = adapter.handleCallback("code")

        assertThat(result).isInstanceOf(LoginResult.Failure::class.java)
        assertThat((result as LoginResult.Failure).error).isEqualTo("not_allowed")
        verify(exactly = 0) { userRepository.upsert(any()) }
    }
}
