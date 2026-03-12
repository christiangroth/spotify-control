package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.EnrichmentError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.AppAlbum
import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.SpotifyCatalogPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class SpotifyCatalogAdapter(
    @param:ConfigProperty(name = "spotify.api.base-url")
    private val apiBaseUrl: String,
    private val httpMetrics: SpotifyHttpMetrics,
    private val throttler: SpotifyRequestThrottler,
) : SpotifyCatalogPort {

    private val httpClient = HttpClient.newHttpClient()
    private val objectMapper = ObjectMapper()

  override fun getArtist(
    userId: UserId,
    accessToken: AccessToken,
    artistId: String,
  ): Either<DomainError, AppArtist?> {
    return try {
      throttler.throttle(DomainOutboxPartition.ToSpotify.key)
      val request = HttpRequest.newBuilder()
        .uri(URI.create("$apiBaseUrl/v1/artists/$artistId"))
        .header("Authorization", "Bearer ${accessToken.value}")
        .GET()
        .build()
      val response = httpMetrics.timed("/v1/artists/{id}") {
        httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      }
      val errorResult = response.checkRateLimitOrError(logger, EnrichmentError.ARTIST_DETAILS_FETCH_FAILED)
      if (errorResult != null) return errorResult
      val json: JsonNode = objectMapper.readTree(response.body())
      if (json.isNull || !json.has("id")) return null.right()
      AppArtist(
        artistId = json.get("id").asText(),
        artistName = json.get("name").asText(),
        genres = json.get("genres")?.map { it.asText() } ?: emptyList(),
        imageLink = json.get("images")?.firstOrNull()?.get("url")?.asText(),
      ).right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error fetching artist details for artist $artistId (user ${userId.value})" }
      EnrichmentError.ARTIST_DETAILS_FETCH_FAILED.left()
    }
  }

  override fun getTrack(
    userId: UserId,
    accessToken: AccessToken,
    trackId: String,
  ): Either<DomainError, String?> {
    return try {
      throttler.throttle(DomainOutboxPartition.ToSpotify.key)
      val request = HttpRequest.newBuilder()
        .uri(URI.create("$apiBaseUrl/v1/tracks/$trackId"))
        .header("Authorization", "Bearer ${accessToken.value}")
        .GET()
        .build()
      val response = httpMetrics.timed("/v1/tracks/{id}") {
        httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      }
      val errorResult = response.checkRateLimitOrError(logger, EnrichmentError.TRACK_DETAILS_FETCH_FAILED)
      if (errorResult != null) return errorResult
      parseAlbumId(objectMapper.readTree(response.body())).right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error fetching track details for track $trackId (user ${userId.value})" }
      EnrichmentError.TRACK_DETAILS_FETCH_FAILED.left()
    }
  }

  private fun parseAlbumId(json: JsonNode): String? {
    if (json.isNull || !json.has("id")) return null
    return json.get("album")?.get("id")?.asText()
  }

  override fun getTracks(
    userId: UserId,
    accessToken: AccessToken,
    trackIds: List<String>,
  ): Either<DomainError, Map<String, String?>> {
    return try {
      throttler.throttle(DomainOutboxPartition.ToSpotify.key)
      val request = HttpRequest.newBuilder()
        .uri(URI.create("$apiBaseUrl/v1/tracks?ids=${trackIds.joinToString(",")}"))
        .header("Authorization", "Bearer ${accessToken.value}")
        .GET()
        .build()
      val response = httpMetrics.timed("/v1/tracks") {
        httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      }
      when (response.statusCode()) {
        HTTP_OK -> parseBulkTrackAlbumIds(objectMapper.readTree(response.body())).right()
        HTTP_RATE_LIMITED -> {
          val retryAfterSeconds = response.headers().firstValue("Retry-After")
            .map { it.toLongOrNull() ?: DEFAULT_RETRY_AFTER_SECONDS }
            .orElse(DEFAULT_RETRY_AFTER_SECONDS)
          logger.warn { "Spotify rate limit exceeded on /v1/tracks, retry after ${retryAfterSeconds}s" }
          SpotifyRateLimitError(Duration.ofSeconds(retryAfterSeconds)).left()
        }
        else -> {
          logger.warn { "Bulk track endpoint unavailable (${response.statusCode()}), falling back to individual requests for ${trackIds.size} tracks" }
          fetchTracksIndividually(userId, accessToken, trackIds)
        }
      }
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error bulk-fetching ${trackIds.size} tracks (user ${userId.value})" }
      EnrichmentError.TRACK_DETAILS_FETCH_FAILED.left()
    }
  }

  private fun fetchTracksIndividually(
    userId: UserId,
    accessToken: AccessToken,
    trackIds: List<String>,
  ): Either<DomainError, Map<String, String?>> {
    val results = mutableMapOf<String, String?>()
    for (trackId in trackIds) {
      when (val result = getTrack(userId, accessToken, trackId)) {
        is Either.Right -> results[trackId] = result.value
        is Either.Left -> return result
      }
    }
    return results.right()
  }

  private fun parseBulkTrackAlbumIds(json: JsonNode): Map<String, String?> {
    val tracks = json.get("tracks") ?: return emptyMap()
    val result = mutableMapOf<String, String?>()
    tracks.forEach { trackNode ->
      if (!trackNode.isNull && trackNode.has("id")) {
        val trackId = trackNode.get("id").asText()
        result[trackId] = trackNode.get("album")?.get("id")?.asText()
      }
    }
    return result
  }

  override fun getAlbum(
    userId: UserId,
    accessToken: AccessToken,
    albumId: String,
  ): Either<DomainError, AppAlbum?> {
    return try {
      throttler.throttle(DomainOutboxPartition.ToSpotify.key)
      val request = HttpRequest.newBuilder()
        .uri(URI.create("$apiBaseUrl/v1/albums/$albumId"))
        .header("Authorization", "Bearer ${accessToken.value}")
        .GET()
        .build()
      val response = httpMetrics.timed("/v1/albums/{id}") {
        httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      }
      val errorResult = response.checkRateLimitOrError(logger, EnrichmentError.ALBUM_DETAILS_FETCH_FAILED)
      if (errorResult != null) return errorResult
      parseAlbumDetails(objectMapper.readTree(response.body())).right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error fetching album details for album $albumId (user ${userId.value})" }
      EnrichmentError.ALBUM_DETAILS_FETCH_FAILED.left()
    }
  }

  private fun parseAlbumDetails(json: JsonNode): AppAlbum? {
    if (json.isNull || !json.has("id")) return null
    return AppAlbum(
      albumId = json.get("id").asText(),
      albumTitle = json.get("name")?.asText(),
      imageLink = json.get("images")?.firstOrNull()?.get("url")?.asText(),
      genres = json.get("genres")?.map { it.asText() } ?: emptyList(),
      artistId = json.get("artists")?.firstOrNull()?.get("id")?.asText(),
    )
  }

    companion object : KLogging()
}
