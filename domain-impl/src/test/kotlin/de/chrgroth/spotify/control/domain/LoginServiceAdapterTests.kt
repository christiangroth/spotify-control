package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.RefreshToken
import de.chrgroth.spotify.control.domain.model.SpotifyProfile
import de.chrgroth.spotify.control.domain.model.SpotifyProfileId
import de.chrgroth.spotify.control.domain.model.SpotifyTokens
import de.chrgroth.spotify.control.domain.model.UserId
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
        every { spotifyAuth.exchangeCode("code") } returns DomainResult.Success(tokens)
        every { spotifyAuth.getUserProfile(AccessToken("access")) } returns DomainResult.Success(profile)
        every { userService.isAllowed(UserId("user-1")) } returns true
        every { tokenEncryption.encrypt(any()) } returns DomainResult.Success("encrypted")
        every { userRepository.upsert(any()) } just runs

        val result = adapter.handleCallback("code")

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        assertThat((result as DomainResult.Success).value).isEqualTo(UserId("user-1"))
        verify { userRepository.upsert(any()) }
    }

    @Test
    fun `not-allowed user returns failure and user is not upserted`() {
        every { spotifyAuth.exchangeCode("code") } returns DomainResult.Success(tokens)
        every { spotifyAuth.getUserProfile(AccessToken("access")) } returns DomainResult.Success(profile)
        every { userService.isAllowed(UserId("user-1")) } returns false

        val result = adapter.handleCallback("code")

        assertThat(result).isInstanceOf(DomainResult.Failure::class.java)
        assertThat((result as DomainResult.Failure).error).isEqualTo(AuthError.USER_NOT_ALLOWED)
        verify(exactly = 0) { userRepository.upsert(any()) }
    }

    @Test
    fun `token exchange failure is propagated`() {
        every { spotifyAuth.exchangeCode("code") } returns DomainResult.Failure(AuthError.TOKEN_EXCHANGE_FAILED)

        val result = adapter.handleCallback("code")

        assertThat(result).isInstanceOf(DomainResult.Failure::class.java)
        assertThat((result as DomainResult.Failure).error).isEqualTo(AuthError.TOKEN_EXCHANGE_FAILED)
        verify(exactly = 0) { userRepository.upsert(any()) }
    }

    @Test
    fun `profile fetch failure is propagated`() {
        every { spotifyAuth.exchangeCode("code") } returns DomainResult.Success(tokens)
        every { spotifyAuth.getUserProfile(AccessToken("access")) } returns DomainResult.Failure(AuthError.PROFILE_FETCH_FAILED)

        val result = adapter.handleCallback("code")

        assertThat(result).isInstanceOf(DomainResult.Failure::class.java)
        assertThat((result as DomainResult.Failure).error).isEqualTo(AuthError.PROFILE_FETCH_FAILED)
        verify(exactly = 0) { userRepository.upsert(any()) }
    }
}
