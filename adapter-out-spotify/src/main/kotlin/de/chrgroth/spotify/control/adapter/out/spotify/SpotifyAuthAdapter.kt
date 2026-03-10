package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.chrgroth.spotify.control.domain.error.AuthError
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.RefreshToken
import de.chrgroth.spotify.control.domain.model.SpotifyProfile
import de.chrgroth.spotify.control.domain.model.SpotifyProfileId
import de.chrgroth.spotify.control.domain.model.SpotifyRefreshedTokens
import de.chrgroth.spotify.control.domain.model.SpotifyTokens
import de.chrgroth.spotify.control.domain.port.out.SpotifyAuthPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class SpotifyAuthAdapter(
    @param:ConfigProperty(name = "spotify.accounts.base-url")
    private val accountsBaseUrl: String,
    @param:ConfigProperty(name = "spotify.api.base-url")
    private val apiBaseUrl: String,
    @param:ConfigProperty(name = "spotify.client-id")
    private val clientId: String,
    @param:ConfigProperty(name = "spotify.client-secret")
    private val clientSecret: String,
    @param:ConfigProperty(name = "app.oauth.redirect-uri")
    private val redirectUri: String,
    private val httpMetrics: SpotifyHttpMetrics,
) : SpotifyAuthPort {

    private val httpClient = HttpClient.newHttpClient()
    private val objectMapper = ObjectMapper()

    override fun exchangeCode(code: String): Either<DomainError, SpotifyTokens> {
        return try {
            val body = "grant_type=authorization_code" +
                "&code=${URLEncoder.encode(code, "UTF-8")}" +
                "&redirect_uri=${URLEncoder.encode(redirectUri, "UTF-8")}"
            val json = postTokenEndpoint(body) ?: return AuthError.TOKEN_EXCHANGE_FAILED.left()
            SpotifyTokens(
                accessToken = AccessToken(json.get("access_token").asText()),
                refreshToken = RefreshToken(json.get("refresh_token").asText()),
                expiresInSeconds = json.get("expires_in").asInt(),
            ).right()
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during token exchange" }
            AuthError.TOKEN_EXCHANGE_FAILED.left()
        }
    }

    override fun getUserProfile(accessToken: AccessToken): Either<DomainError, SpotifyProfile> {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$apiBaseUrl/v1/me"))
                .header("Authorization", "Bearer ${accessToken.value}")
                .GET()
                .build()
            val response = httpMetrics.timed(request.uri()) {
                httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            }
            val errorResult = response.checkRateLimitOrError(logger, AuthError.PROFILE_FETCH_FAILED)
            if (errorResult != null) return errorResult
            val json: JsonNode = objectMapper.readTree(response.body())
            SpotifyProfile(
                id = SpotifyProfileId(json.get("id").asText()),
                displayName = json.get("display_name").asText(),
            ).right()
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during profile fetch" }
            AuthError.PROFILE_FETCH_FAILED.left()
        }
    }

    override fun refreshToken(refreshToken: RefreshToken): Either<DomainError, SpotifyRefreshedTokens> {
        return try {
            val body = "grant_type=refresh_token" +
                "&refresh_token=${URLEncoder.encode(refreshToken.value, "UTF-8")}"
            val json = postTokenEndpoint(body) ?: return AuthError.TOKEN_REFRESH_FAILED.left()
            SpotifyRefreshedTokens(
                accessToken = AccessToken(json.get("access_token").asText()),
                refreshToken = json.get("refresh_token")?.takeIf { !it.isNull }?.asText()?.let { RefreshToken(it) },
                expiresInSeconds = json.get("expires_in").asInt(),
            ).right()
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during token refresh" }
            AuthError.TOKEN_REFRESH_FAILED.left()
        }
    }

    private fun postTokenEndpoint(body: String): JsonNode? {
        val credentials = Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$accountsBaseUrl/api/token"))
            .header("Authorization", "Basic $credentials")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = httpMetrics.timed(request.uri()) {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }
        if (response.statusCode() != HTTP_OK) {
            logger.error { "Spotify token endpoint request failed: ${response.statusCode()} - ${response.body()}" }
            return null
        }
        return objectMapper.readTree(response.body())
    }

    companion object : KLogging()
}
