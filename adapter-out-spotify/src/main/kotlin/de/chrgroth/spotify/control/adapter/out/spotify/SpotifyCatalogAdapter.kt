package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.EnrichmentError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.AlbumId
import de.chrgroth.spotify.control.domain.model.AppAlbum
import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.ArtistId
import de.chrgroth.spotify.control.domain.model.TrackSyncResult
import de.chrgroth.spotify.control.domain.model.TrackId
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
  ): Either<DomainError, TrackSyncResult?> {
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
      parseTrackSyncResult(objectMapper.readTree(response.body())).right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error fetching track details for track $trackId (user ${userId.value})" }
      EnrichmentError.TRACK_DETAILS_FETCH_FAILED.left()
    }
  }

  private fun parseTrackSyncResult(json: JsonNode): TrackSyncResult? {
    val artists = json.takeIf { !it.isNull && it.has("id") }?.get("artists")
    val primaryArtistId = artists?.firstOrNull()?.get("id")?.asText() ?: return null
    val primaryArtistName = artists.firstOrNull()?.get("name")?.asText()

    val albumNode = json.get("album")
    val albumId = albumNode?.get("id")?.asText() ?: return null
    val albumName = albumNode.get("name")?.asText()

    val track = AppTrack(
      id = TrackId(json.get("id").asText()),
      title = json.get("name")?.asText() ?: "",
      albumId = AlbumId(albumId),
      albumName = albumName,
      artistId = ArtistId(primaryArtistId),
      artistName = primaryArtistName,
      additionalArtistIds = artists.additionalIds { get("id").asText() }?.map { ArtistId(it) } ?: emptyList(),
      additionalArtistNames = artists.additionalIds { get("name").asText() },
      discNumber = json.get("disc_number")?.asInt(),
      durationMs = json.get("duration_ms")?.asLong(),
      trackNumber = json.get("track_number")?.asInt(),
      type = json.get("type")?.asText(),
    )

    val albumArtists = albumNode.get("artists")
    val album = AppAlbum(
      id = AlbumId(albumId),
      totalTracks = albumNode.get("total_tracks")?.asInt(),
      title = albumName,
      imageLink = albumNode.get("images")?.firstOrNull()?.get("url")?.asText(),
      releaseDate = albumNode.get("release_date")?.asText(),
      releaseDatePrecision = albumNode.get("release_date_precision")?.asText(),
      type = albumNode.get("album_type")?.asText(),
      artistId = albumArtists?.firstOrNull()?.get("id")?.asText()?.let { ArtistId(it) },
      artistName = albumArtists?.firstOrNull()?.get("name")?.asText(),
      additionalArtistIds = albumArtists.additionalIds { get("id").asText() }?.map { ArtistId(it) },
      additionalArtistNames = albumArtists.additionalIds { get("name").asText() },
    )

    return TrackSyncResult(track = track, album = album)
  }

  private fun JsonNode?.additionalIds(extractor: JsonNode.() -> String): List<String>? =
    if (this == null || size() <= 1) null else (1 until size()).map { get(it).extractor() }

    companion object : KLogging()
}
