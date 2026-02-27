package de.chrgroth.spotify.control.adapter.out.spotify

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.chrgroth.spotify.control.domain.AuthError
import de.chrgroth.spotify.control.domain.DomainResult
import de.chrgroth.spotify.control.domain.SpotifyError
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
    @ConfigProperty(name = "spotify.accounts.base-url", defaultValue = "https://accounts.spotify.com")
    private val accountsBaseUrl: String,
    @ConfigProperty(name = "spotify.api.base-url", defaultValue = "https://api.spotify.com")
    private val apiBaseUrl: String,
    @ConfigProperty(name = "spotify.client-id")
    private val clientId: String,
    @ConfigProperty(name = "spotify.client-secret")
    private val clientSecret: String,
    @ConfigProperty(name = "app.oauth.redirect-uri")
    private val redirectUri: String,
) : SpotifyAuthPort {

    private val httpClient = HttpClient.newHttpClient()
    private val objectMapper = ObjectMapper()

    override fun exchangeCode(code: String): DomainResult<SpotifyTokens> {
        return try {
            val body = "grant_type=authorization_code" +
                "&code=${URLEncoder.encode(code, "UTF-8")}" +
                "&redirect_uri=${URLEncoder.encode(redirectUri, "UTF-8")}"
            val json = when (val r = postTokenEndpoint(body)) {
                is DomainResult.Success -> r.value
                is DomainResult.Failure -> return DomainResult.Failure(AuthError.TOKEN_EXCHANGE_FAILED)
            }
            DomainResult.Success(
                SpotifyTokens(
                    accessToken = AccessToken(json.get("access_token").asText()),
                    refreshToken = RefreshToken(json.get("refresh_token").asText()),
                    expiresInSeconds = json.get("expires_in").asInt(),
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during token exchange" }
            DomainResult.Failure(AuthError.TOKEN_EXCHANGE_FAILED)
        }
    }

    override fun getUserProfile(accessToken: AccessToken): DomainResult<SpotifyProfile> {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$apiBaseUrl/v1/me"))
                .header("Authorization", "Bearer ${accessToken.value}")
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != HTTP_OK) {
                logger.error { "Spotify profile fetch failed: ${response.statusCode()} - ${response.body()}" }
                return DomainResult.Failure(AuthError.PROFILE_FETCH_FAILED)
            }
            val json: JsonNode = objectMapper.readTree(response.body())
            DomainResult.Success(
                SpotifyProfile(
                    id = SpotifyProfileId(json.get("id").asText()),
                    displayName = json.get("display_name").asText(),
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during profile fetch" }
            DomainResult.Failure(AuthError.PROFILE_FETCH_FAILED)
        }
    }

    override fun refreshToken(refreshToken: RefreshToken): DomainResult<SpotifyRefreshedTokens> {
        return try {
            val body = "grant_type=refresh_token" +
                "&refresh_token=${URLEncoder.encode(refreshToken.value, "UTF-8")}"
            val json = when (val r = postTokenEndpoint(body)) {
                is DomainResult.Success -> r.value
                is DomainResult.Failure -> return DomainResult.Failure(SpotifyError.TOKEN_REFRESH_FAILED)
            }
            DomainResult.Success(
                SpotifyRefreshedTokens(
                    accessToken = AccessToken(json.get("access_token").asText()),
                    refreshToken = json.get("refresh_token")?.takeIf { !it.isNull }?.asText()?.let { RefreshToken(it) },
                    expiresInSeconds = json.get("expires_in").asInt(),
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during token refresh" }
            DomainResult.Failure(SpotifyError.TOKEN_REFRESH_FAILED)
        }
    }

    private fun postTokenEndpoint(body: String): DomainResult<JsonNode> {
        return try {
            val credentials = Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$accountsBaseUrl/api/token"))
                .header("Authorization", "Basic $credentials")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != HTTP_OK) {
                logger.error { "Spotify token endpoint request failed: ${response.statusCode()} - ${response.body()}" }
                return DomainResult.Failure(SpotifyError.UNEXPECTED)
            }
            DomainResult.Success(objectMapper.readTree(response.body()))
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error calling Spotify token endpoint" }
            DomainResult.Failure(SpotifyError.UNEXPECTED)
        }
    }

    companion object : KLogging() {
        private const val HTTP_OK = 200
    }
}
