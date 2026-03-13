package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyArtistResponse
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyTrackResponse
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
      val artist = spotifyJson.decodeFromString<SpotifyArtistResponse>(response.body())
      val allGenres = artist.genres
      AppArtist(
        artistId = artist.id,
        artistName = artist.name,
        genre = allGenres.firstOrNull(),
        additionalGenres = allGenres.drop(1).ifEmpty { null },
        imageLink = artist.images.firstOrNull()?.url,
        type = artist.type,
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
      parseTrackSyncResult(spotifyJson.decodeFromString<SpotifyTrackResponse>(response.body())).right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error fetching track details for track $trackId (user ${userId.value})" }
      EnrichmentError.TRACK_DETAILS_FETCH_FAILED.left()
    }
  }

  private fun parseTrackSyncResult(track: SpotifyTrackResponse): TrackSyncResult? {
    val primaryArtist = track.artists.firstOrNull() ?: return null
    val albumRef = track.album ?: return null

    val appTrack = AppTrack(
      id = TrackId(track.id),
      title = track.name,
      albumId = AlbumId(albumRef.id),
      albumName = albumRef.name,
      artistId = ArtistId(primaryArtist.id),
      artistName = primaryArtist.name,
      additionalArtistIds = track.artists.additionalItems { ArtistId(id) } ?: emptyList(),
      additionalArtistNames = track.artists.additionalItems { name },
      discNumber = track.discNumber,
      durationMs = track.durationMs,
      trackNumber = track.trackNumber,
      type = track.type,
    )

    val appAlbum = AppAlbum(
      id = AlbumId(albumRef.id),
      totalTracks = albumRef.totalTracks,
      title = albumRef.name,
      imageLink = albumRef.images.firstOrNull()?.url,
      releaseDate = albumRef.releaseDate,
      releaseDatePrecision = albumRef.releaseDatePrecision,
      type = albumRef.albumType,
      artistId = albumRef.artists.firstOrNull()?.let { ArtistId(it.id) },
      artistName = albumRef.artists.firstOrNull()?.name,
      additionalArtistIds = albumRef.artists.additionalItems { ArtistId(id) },
      additionalArtistNames = albumRef.artists.additionalItems { name },
    )

    return TrackSyncResult(track = appTrack, album = appAlbum)
  }

  private fun <T, R> List<T>.additionalItems(extractor: T.() -> R): List<R>? =
    if (size <= 1) null else (1 until size).map { get(it).extractor() }

    companion object : KLogging()
}

