package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.PlaybackError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.SpotifyPlaybackPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Clock
import kotlin.time.Instant

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class SpotifyPlaybackAdapter(
    @param:ConfigProperty(name = "spotify.api.base-url")
    private val apiBaseUrl: String,
    private val httpMetrics: SpotifyHttpMetrics,
) : SpotifyPlaybackPort {

    private val httpClient = HttpClient.newHttpClient()
    private val objectMapper = ObjectMapper()

  override fun getCurrentlyPlaying(userId: UserId, accessToken: AccessToken): Either<DomainError, CurrentlyPlayingItem?> {
    return try {
      val request = HttpRequest.newBuilder()
        .uri(URI.create("$apiBaseUrl/v1/me/player/currently-playing"))
        .header("Authorization", "Bearer ${accessToken.value}")
        .GET()
        .build()
      val response = httpMetrics.timed(request.uri()) {
        httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      }
      if (response.statusCode() == HTTP_NO_CONTENT) {
        return null.right()
      }
      val errorResult = response.checkRateLimitOrError(logger, PlaybackError.CURRENTLY_PLAYING_FETCH_FAILED)
      if (errorResult != null) return errorResult
      val json: JsonNode = objectMapper.readTree(response.body())
      parseCurrentlyPlayingItem(userId, json).right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during currently playing fetch" }
      PlaybackError.CURRENTLY_PLAYING_FETCH_FAILED.left()
    }
  }

  private fun parseCurrentlyPlayingItem(userId: UserId, json: JsonNode): CurrentlyPlayingItem? {
    val track = json.get("item")
    val type = track?.get("type")?.asText()
    if (type != "track") {
      logger.info { "Ignoring non-track currently playing event of type '$type'" }
      return null
    }
    val isLocal = track.get("is_local")?.takeIf { !it.isNull }?.asBoolean() ?: false
    if (isLocal) {
      logger.info { "Ignoring local currently playing track '${track.get("name")?.asText()}'" }
      return null
    }
    return CurrentlyPlayingItem(
      spotifyUserId = userId,
      trackId = track.get("id").asText(),
      trackName = track.get("name").asText(),
      artistIds = track.get("artists").map { it.get("id").asText() },
      artistNames = track.get("artists").map { it.get("name").asText() },
      progressMs = json.get("progress_ms")?.takeIf { !it.isNull }?.asLong() ?: 0L,
      durationMs = track.get("duration_ms")?.takeIf { !it.isNull }?.asLong() ?: 0L,
      isPlaying = json.get("is_playing")?.takeIf { !it.isNull }?.asBoolean() ?: false,
      observedAt = Clock.System.now(),
    )
  }

    override fun getRecentlyPlayed(userId: UserId, accessToken: AccessToken, after: Instant?): Either<DomainError, List<RecentlyPlayedItem>> {
        return try {
            val allItems = mutableListOf<RecentlyPlayedItem>()
            val afterParam = after?.let { "&after=${it.toEpochMilliseconds()}" } ?: ""
            var nextUrl: String? = "$apiBaseUrl/v1/me/player/recently-played?limit=50$afterParam"
            while (nextUrl != null) {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(nextUrl))
                    .header("Authorization", "Bearer ${accessToken.value}")
                    .GET()
                    .build()
                val response = httpMetrics.timed(request.uri()) {
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                }
                val errorResult = response.checkRateLimitOrError(logger, PlaybackError.RECENTLY_PLAYED_FETCH_FAILED)
                if (errorResult != null) return errorResult
                val json: JsonNode = objectMapper.readTree(response.body())
                val items = json.get("items")
                if (items != null) {
                    items.mapNotNullTo(allItems) { item ->
                        val track = item.get("track")
                        val type = track?.get("type")?.asText()
                        if (type != "track") {
                            logger.info { "Ignoring non-track playback event of type '$type'" }
                            return@mapNotNullTo null
                        }
                        val isLocal = track.get("is_local")?.takeIf { !it.isNull }?.asBoolean() ?: false
                        if (isLocal) {
                            logger.info { "Ignoring local track '${track.get("name")?.asText()}'" }
                            return@mapNotNullTo null
                        }
                        RecentlyPlayedItem(
                            spotifyUserId = userId,
                            trackId = track.get("id").asText(),
                            trackName = track.get("name").asText(),
                            artistIds = track.get("artists").map { it.get("id").asText() },
                            artistNames = track.get("artists").map { it.get("name").asText() },
                            playedAt = Instant.parse(item.get("played_at").asText()),
                        )
                    }
                }
                nextUrl = json.get("next")?.takeIf { !it.isNull }?.asText()
            }
            allItems.right()
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during recently played fetch" }
            PlaybackError.RECENTLY_PLAYED_FETCH_FAILED.left()
        }
    }

    companion object : KLogging() {
        private const val HTTP_NO_CONTENT = 204
    }
}
