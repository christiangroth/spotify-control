package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.PlaybackError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.SpotifyRecentlyPlayedPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.time.Instant

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class SpotifyRecentlyPlayedAdapter(
    @ConfigProperty(name = "spotify.api.base-url", defaultValue = "https://api.spotify.com")
    private val apiBaseUrl: String,
) : SpotifyRecentlyPlayedPort {

    private val httpClient = HttpClient.newHttpClient()
    private val objectMapper = ObjectMapper()

    override fun getRecentlyPlayed(userId: UserId, accessToken: AccessToken): Either<DomainError, List<RecentlyPlayedItem>> {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$apiBaseUrl/v1/me/player/recently-played?limit=50"))
                .header("Authorization", "Bearer ${accessToken.value}")
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            val errorResult = checkResponseError(response)
            if (errorResult != null) return errorResult
            val json: JsonNode = objectMapper.readTree(response.body())
            val items = json.get("items") ?: return emptyList<RecentlyPlayedItem>().right()
            items.map { item ->
                val track = item.get("track")
                RecentlyPlayedItem(
                    spotifyUserId = userId,
                    trackId = track.get("id").asText(),
                    trackName = track.get("name").asText(),
                    artistIds = track.get("artists").map { it.get("id").asText() },
                    artistNames = track.get("artists").map { it.get("name").asText() },
                    playedAt = Instant.parse(item.get("played_at").asText()),
                )
            }.right()
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during recently played fetch" }
            PlaybackError.RECENTLY_PLAYED_FETCH_FAILED.left()
        }
    }

    private fun checkResponseError(response: HttpResponse<String>): Either<DomainError, Nothing>? {
        if (response.statusCode() == HTTP_RATE_LIMITED) {
            val retryAfterSeconds = response.headers().firstValue("Retry-After")
                .map { it.toLongOrNull() ?: DEFAULT_RETRY_AFTER_SECONDS }
                .orElse(DEFAULT_RETRY_AFTER_SECONDS)
            logger.warn { "Spotify rate limit exceeded, retry after ${retryAfterSeconds}s" }
            return SpotifyRateLimitError(Duration.ofSeconds(retryAfterSeconds)).left()
        }
        if (response.statusCode() != HTTP_OK) {
            logger.error { "Spotify recently played fetch failed: ${response.statusCode()} - ${response.body()}" }
            return PlaybackError.RECENTLY_PLAYED_FETCH_FAILED.left()
        }
        return null
    }

    companion object : KLogging() {
        private const val HTTP_OK = 200
        private const val HTTP_RATE_LIMITED = 429
        private const val DEFAULT_RETRY_AFTER_SECONDS = 60L
    }
}
