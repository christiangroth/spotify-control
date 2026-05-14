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
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
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
import kotlin.time.Duration.Companion.seconds

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
      SpotifyApiAuthContext.set(accessToken)
      throttler.throttle(DomainOutboxPartition.ToSpotify.key)
      val artist = httpMetrics.timed("/v1/artists/{id}") { apiClient.getArtist(artistId) }
      parseArtist(artist).right()
    } catch (e: SpotifyRateLimitException) {
      SpotifyRateLimitError(e.retryAfterSeconds.seconds).left()
    } catch (e: SpotifyApiException) {
      logger.error { "Spotify artist fetch failed for $artistId: ${e.statusCode}" }
      SyncError.ARTIST_DETAILS_FETCH_FAILED.left()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error fetching artist details for artist $artistId (user ${userId.value})" }
      SyncError.ARTIST_DETAILS_FETCH_FAILED.left()
    } finally {
      SpotifyApiAuthContext.clear()
    }
  }

  override fun getAlbum(
    userId: UserId,
    accessToken: AccessToken,
    albumId: String,
  ): Either<DomainError, AlbumSyncResult> {
    return try {
      SpotifyApiAuthContext.set(accessToken)
      throttler.throttle(DomainOutboxPartition.ToSpotify.key)
      val albumResponse = httpMetrics.timed("/v1/albums/{id}") { apiClient.getAlbum(albumId) }
      val appAlbum = parseAlbum(albumResponse)
      val allTracks = albumResponse.tracks.items.toMutableList()
      var nextOffset: Int? = albumResponse.tracks.next?.queryParamInt("offset")
      while (nextOffset != null) {
        throttler.throttle(DomainOutboxPartition.ToSpotify.key)
        val nextPage = httpMetrics.timed("/v1/albums/{id}/tracks") {
          apiClient.getAlbumTracks(albumId, ALBUM_TRACKS_PAGE_SIZE, nextOffset)
        }
        allTracks.addAll(nextPage.items)
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
    } catch (e: SpotifyRateLimitException) {
      SpotifyRateLimitError(e.retryAfterSeconds.seconds).left()
    } catch (e: SpotifyApiException) {
      logger.error { "Spotify album fetch failed for $albumId: ${e.statusCode}" }
      SyncError.TRACK_DETAILS_FETCH_FAILED.left()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error fetching album tracks for album $albumId (user ${userId.value})" }
      SyncError.TRACK_DETAILS_FETCH_FAILED.left()
    } finally {
      SpotifyApiAuthContext.clear()
    }
  }

  override fun getArtistAlbumsPage(
    userId: UserId,
    accessToken: AccessToken,
    artistId: String,
    nextUrl: String?,
  ): Either<DomainError, ArtistAlbumsPage> {
    return try {
      SpotifyApiAuthContext.set(accessToken)
      val offset = nextUrl?.queryParamInt("offset")
      throttler.throttle(DomainOutboxPartition.ToSpotify.key)
      val page = httpMetrics.timed("/v1/artists/{id}/albums") {
        apiClient.getArtistAlbums(artistId, ARTIST_ALBUMS_PAGE_SIZE, offset)
      }
      ArtistAlbumsPage(albumIds = (page.items ?: emptyList()).mapNotNull { it.id }, nextUrl = page.next).right()
    } catch (e: SpotifyRateLimitException) {
      SpotifyRateLimitError(e.retryAfterSeconds.seconds).left()
    } catch (e: SpotifyApiException) {
      logger.error { "Spotify artist albums fetch failed for $artistId: ${e.statusCode}" }
      SyncError.ARTIST_DETAILS_FETCH_FAILED.left()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error fetching album ids for artist $artistId (user ${userId.value})" }
      SyncError.ARTIST_DETAILS_FETCH_FAILED.left()
    } finally {
      SpotifyApiAuthContext.clear()
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

  private fun parseAlbum(album: AlbumObject): AppAlbum =
    AppAlbum(
      id = AlbumId(album.id),
      totalTracks = album.totalTracks,
      title = album.name,
      imageLink = album.images.firstOrNull()?.url,
      releaseDate = album.releaseDate,
      releaseDatePrecision = album.releaseDatePrecision.value,
      type = album.albumType.value,
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

  companion object : KLogging() {
    private const val ALBUM_TRACKS_PAGE_SIZE = 50
    private const val ARTIST_ALBUMS_PAGE_SIZE = 10
  }
}
