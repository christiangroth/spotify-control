package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.adapter.out.spotify.model.AlbumObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.ArtistObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.PagingArtistDiscographyAlbumObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.PagingSimplifiedTrackObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.SimplifiedTrackObject
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.SyncError
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AlbumSyncResult
import de.chrgroth.spotify.control.domain.model.catalog.AppAlbum
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.ArtistAlbumsPage
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.catalog.SpotifyCatalogPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.rest.client.inject.RestClient
import kotlin.time.Clock

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class SpotifyCatalogAdapter(
  private val httpMetrics: SpotifyHttpMetrics,
  private val throttler: SpotifyRequestThrottler,
  @param:RestClient private val apiClient: SpotifyApiRestClient,
) : SpotifyCatalogPort {

  override fun getArtist(
    userId: UserId,
    accessToken: AccessToken,
    artistId: String,
  ): Either<DomainError, AppArtist?> {
    return try {
      throttler.throttle(DomainOutboxPartition.ToSpotify.key)
      val response = httpMetrics.timed("/v1/artists/{id}") {
        apiClient.getArtist("Bearer ${accessToken.value}", artistId)
      }
      val errorResult = response.checkRateLimitOrError(logger, "/v1/artists/{id}", SyncError.ARTIST_DETAILS_FETCH_FAILED)
      if (errorResult != null) return errorResult
      parseArtist(spotifyJson.decodeFromString<ArtistObject>(response.readEntity(String::class.java))).right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error fetching artist details for artist $artistId (user ${userId.value})" }
      SyncError.ARTIST_DETAILS_FETCH_FAILED.left()
    }
  }

  override fun getAlbum(
    userId: UserId,
    accessToken: AccessToken,
    albumId: String,
  ): Either<DomainError, AlbumSyncResult> {
    return try {
      throttler.throttle(DomainOutboxPartition.ToSpotify.key)
      val response = httpMetrics.timed("/v1/albums/{id}") {
        apiClient.getAlbum("Bearer ${accessToken.value}", albumId)
      }
      val errorResult = response.checkRateLimitOrError(logger, "/v1/albums/{id}", SyncError.TRACK_DETAILS_FETCH_FAILED)
      if (errorResult != null) return errorResult
      val albumResponse = spotifyJson.decodeFromString<AlbumObject>(response.readEntity(String::class.java))
      val appAlbum = parseAlbum(albumId, albumResponse)
      val allTracks = albumResponse.tracks?.items?.filterNotNull()?.toMutableList() ?: mutableListOf()
      var nextOffset: Int? = albumResponse.tracks?.next?.queryParamInt("offset")
      while (nextOffset != null) {
        throttler.throttle(DomainOutboxPartition.ToSpotify.key)
        val nextResponse = httpMetrics.timed("/v1/albums/{id}/tracks") {
          apiClient.getAlbumTracks("Bearer ${accessToken.value}", albumId, 50, nextOffset)
        }
        val nextError = nextResponse.checkRateLimitOrError(logger, "/v1/albums/{id}/tracks", SyncError.TRACK_DETAILS_FETCH_FAILED)
        if (nextError != null) return nextError
        val nextPage = spotifyJson.decodeFromString<PagingSimplifiedTrackObject>(nextResponse.readEntity(String::class.java))
        allTracks.addAll(nextPage.items.filterNotNull())
        nextOffset = nextPage.next?.queryParamInt("offset")
      }
      val parsedTracks = allTracks.mapNotNull { parseAlbumTrack(it, appAlbum) }
      val droppedCount = allTracks.size - parsedTracks.size
      if (droppedCount > 0) {
        logger.warn {
          "Album $albumId: dropped $droppedCount track(s) without id or primary artist" +
            " (fetched ${allTracks.size}, album reports ${appAlbum.totalTracks} total)"
        }
      }
      AlbumSyncResult(
        album = appAlbum,
        tracks = parsedTracks,
      ).right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error fetching album tracks for album $albumId (user ${userId.value})" }
      SyncError.TRACK_DETAILS_FETCH_FAILED.left()
    }
  }

  override fun getArtistAlbumsPage(
    userId: UserId,
    accessToken: AccessToken,
    artistId: String,
    nextUrl: String?,
  ): Either<DomainError, ArtistAlbumsPage> {
    return try {
      val offset = nextUrl?.queryParamInt("offset")
      throttler.throttle(DomainOutboxPartition.ToSpotify.key)
      val response = httpMetrics.timed("/v1/artists/{id}/albums") {
        apiClient.getArtistAlbums("Bearer ${accessToken.value}", artistId, 10, offset)
      }
      val errorResult = response.checkRateLimitOrError(logger, "/v1/artists/{id}/albums", SyncError.ARTIST_DETAILS_FETCH_FAILED)
      if (errorResult != null) return errorResult
      val page = spotifyJson.decodeFromString<PagingArtistDiscographyAlbumObject>(response.readEntity(String::class.java))
      ArtistAlbumsPage(albumIds = page.items.mapNotNull { it.id }, nextUrl = page.next).right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error fetching album ids for artist $artistId (user ${userId.value})" }
      SyncError.ARTIST_DETAILS_FETCH_FAILED.left()
    }
  }

  private fun parseArtist(artist: ArtistObject): AppArtist =
    AppArtist(
      id = ArtistId(artist.id ?: ""),
      artistName = artist.name ?: "",
      imageLink = artist.images?.firstOrNull()?.url,
      type = artist.type?.value,
      lastSync = Clock.System.now(),
    )

  private fun parseAlbum(albumId: String, album: AlbumObject): AppAlbum =
    AppAlbum(
      id = AlbumId(album.id ?: albumId),
      totalTracks = album.totalTracks,
      title = album.name ?: "",
      imageLink = album.images.firstOrNull()?.url,
      releaseDate = album.releaseDate,
      releaseDatePrecision = album.releaseDatePrecision,
      type = album.albumType,
      artistId = album.artists.firstOrNull()?.id?.let { ArtistId(it) },
      artistName = album.artists.firstOrNull()?.name,
      additionalArtistIds = album.artists.additionalItems { id?.let { ArtistId(it) } }?.filterNotNull(),
      additionalArtistNames = album.artists.additionalItems { name }?.filterNotNull(),
      lastSync = Clock.System.now(),
    )

  private fun parseAlbumTrack(track: SimplifiedTrackObject, album: AppAlbum): AppTrack? {
    val trackId = track.id ?: return null
    val (primaryArtistId, primaryArtistName) = (track.artists ?: emptyList()).firstOrNull()
      ?.let { artist -> artist.id?.let { id -> id to artist.name } }
      ?: return null
    return AppTrack(
      id = TrackId(trackId),
      title = track.name ?: "",
      albumId = album.id,
      albumName = album.title,
      artistId = ArtistId(primaryArtistId),
      artistName = primaryArtistName ?: "",
      additionalArtistIds = (track.artists ?: emptyList()).additionalItems { id?.let { ArtistId(it) } }?.filterNotNull() ?: emptyList(),
      additionalArtistNames = (track.artists ?: emptyList()).additionalItems { name }?.filterNotNull(),
      discNumber = track.discNumber,
      durationMs = track.durationMs?.toLong(),
      trackNumber = track.trackNumber,
      type = track.type,
      lastSync = album.lastSync,
    )
  }

  private fun <T, R> List<T>.additionalItems(extractor: T.() -> R): List<R>? =
    if (size <= 1) null else (1 until size).map { get(it).extractor() }

  companion object : KLogging()
}
