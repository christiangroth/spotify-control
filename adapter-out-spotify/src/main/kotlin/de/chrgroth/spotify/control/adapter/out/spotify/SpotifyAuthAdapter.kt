package de.chrgroth.spotify.control.adapter.out.spotify

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.chrgroth.spotify.control.domain.model.SpotifyProfile
import de.chrgroth.spotify.control.domain.model.SpotifyTokens
import de.chrgroth.spotify.control.domain.port.out.SpotifyAuthPort
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64

@ApplicationScoped
@Suppress("Unused")
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

    override fun exchangeCode(code: String): SpotifyTokens {
        val body = "grant_type=authorization_code" +
            "&code=${URLEncoder.encode(code, "UTF-8")}" +
            "&redirect_uri=${URLEncoder.encode(redirectUri, "UTF-8")}"
        val credentials = Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$accountsBaseUrl/api/token"))
            .header("Authorization", "Basic $credentials")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == HTTP_OK) { "Spotify token exchange failed: ${response.statusCode()}" }
        val json: JsonNode = objectMapper.readTree(response.body())
        return SpotifyTokens(
            accessToken = json.get("access_token").asText(),
            refreshToken = json.get("refresh_token").asText(),
            expiresInSeconds = json.get("expires_in").asInt(),
        )
    }

    override fun getUserProfile(accessToken: String): SpotifyProfile {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$apiBaseUrl/v1/me"))
            .header("Authorization", "Bearer $accessToken")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == HTTP_OK) { "Spotify profile fetch failed: ${response.statusCode()}" }
        val json: JsonNode = objectMapper.readTree(response.body())
        return SpotifyProfile(
            id = json.get("id").asText(),
            displayName = json.get("display_name").asText(),
        )
    }

    companion object {
        private const val HTTP_OK = 200
    }
}
