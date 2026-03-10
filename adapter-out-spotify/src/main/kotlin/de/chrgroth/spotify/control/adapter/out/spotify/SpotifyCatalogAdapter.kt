package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.EnrichmentError
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
      val response = httpMetrics.timed(request.uri()) {
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
      val response = httpMetrics.timed(request.uri()) {
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
      val response = httpMetrics.timed(request.uri()) {
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
