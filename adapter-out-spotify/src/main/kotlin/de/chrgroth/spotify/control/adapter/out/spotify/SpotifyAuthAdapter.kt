package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.adapter.out.spotify.model.PrivateUserObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyTokenResponse
import de.chrgroth.spotify.control.domain.error.AuthError
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.RefreshToken
import de.chrgroth.spotify.control.domain.model.user.SpotifyProfile
import de.chrgroth.spotify.control.domain.model.user.SpotifyProfileId
import de.chrgroth.spotify.control.domain.model.user.SpotifyRefreshedTokens
import de.chrgroth.spotify.control.domain.model.user.SpotifyTokens
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAuthPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.rest.client.inject.RestClient
import kotlin.time.Duration.Companion.seconds

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class SpotifyAuthAdapter(
  @param:ConfigProperty(name = "app.oauth.redirect-uri")
  private val redirectUri: String,
  private val httpMetrics: SpotifyHttpMetrics,
  @param:RestClient private val accountsClient: SpotifyAccountsRestClient,
  @param:RestClient private val apiClient: SpotifyApiRestClient,
) : SpotifyAuthPort {

  override fun exchangeCode(code: String): Either<DomainError, SpotifyTokens> {
    return try {
      val tokenResponse = httpMetrics.timed("/api/token") {
        accountsClient.exchangeCode("authorization_code", code, redirectUri)
      }
      SpotifyTokens(
        accessToken = AccessToken(tokenResponse.accessToken),
        refreshToken = RefreshToken(tokenResponse.refreshToken ?: return AuthError.TOKEN_EXCHANGE_FAILED.left()),
        expiresInSeconds = tokenResponse.expiresIn,
      ).right()
    } catch (e: SpotifyApiException) {
      logger.error { "Spotify token exchange failed: ${e.statusCode}" }
      AuthError.TOKEN_EXCHANGE_FAILED.left()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during token exchange" }
      AuthError.TOKEN_EXCHANGE_FAILED.left()
    }
  }

  override fun getUserProfile(accessToken: AccessToken): Either<DomainError, SpotifyProfile> {
    return try {
      SpotifyApiAuthContext.set(accessToken)
      val profile = httpMetrics.timed("/v1/me") { apiClient.getCurrentUserProfile() }
      SpotifyProfile(
        id = SpotifyProfileId(profile.id ?: return AuthError.PROFILE_FETCH_FAILED.left()),
        displayName = profile.displayName ?: "",
      ).right()
    } catch (e: SpotifyRateLimitException) {
      SpotifyRateLimitError(e.retryAfterSeconds.seconds).left()
    } catch (e: SpotifyApiException) {
      logger.error { "Spotify profile fetch failed: ${e.statusCode}" }
      AuthError.PROFILE_FETCH_FAILED.left()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during profile fetch" }
      AuthError.PROFILE_FETCH_FAILED.left()
    } finally {
      SpotifyApiAuthContext.clear()
    }
  }

  override fun refreshToken(refreshToken: RefreshToken): Either<DomainError, SpotifyRefreshedTokens> {
    return try {
      val tokenResponse = httpMetrics.timed("/api/token") {
        accountsClient.refreshToken("refresh_token", refreshToken.value)
      }
      SpotifyRefreshedTokens(
        accessToken = AccessToken(tokenResponse.accessToken),
        refreshToken = tokenResponse.refreshToken?.let { RefreshToken(it) },
        expiresInSeconds = tokenResponse.expiresIn,
      ).right()
    } catch (e: SpotifyApiException) {
      logger.error { "Spotify token refresh failed: ${e.statusCode}" }
      AuthError.TOKEN_REFRESH_FAILED.left()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during token refresh" }
      AuthError.TOKEN_REFRESH_FAILED.left()
    }
  }

  companion object : KLogging()
}
