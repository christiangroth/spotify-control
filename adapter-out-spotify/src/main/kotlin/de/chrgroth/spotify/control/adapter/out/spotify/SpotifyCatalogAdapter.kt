package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.adapter.out.spotify.model.AlbumObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.ArtistDiscographyAlbumObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.ArtistObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.ImageObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.PagingArtistDiscographyAlbumObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.PagingSimplifiedTrackObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.SimplifiedArtistObject
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
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.catalog.SpotifyCatalogPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.rest.client.inject.RestClient
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class SpotifyCatalogAdapter(
  private val httpMetrics: SpotifyHttpMetrics,
  private val throttler: SpotifyRequestThrottler,
  @param:RestClient private val apiClient: SpotifyApiRestClient,
) : SpotifyCatalogPort {

  override fun getArtist(
    accessToken: AccessToken,
    artistId: String,
  ): Either<DomainError, AppArtist?> {
    return try {
      SpotifyApiAuthContext.set(accessToken)
      throttler.throttle(DomainOutboxPartition.ToSpotifyCatalog.key)
      val artist = httpMetrics.timed("/v1/artists/{id}") { apiClient.getArtist(artistId) }
      parseArtist(artist).right()
    } catch (e: SpotifyRateLimitException) {
      SpotifyRateLimitError(e.retryAfterSeconds.seconds).left()
    } catch (e: SpotifyApiException) {
      logger.error { "Spotify artist fetch failed for $artistId: ${e.statusCode}" }
      SyncError.ARTIST_DETAILS_FETCH_FAILED.left()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error fetching artist details for artist $artistId" }
      SyncError.ARTIST_DETAILS_FETCH_FAILED.left()
    } finally {
      SpotifyApiAuthContext.clear()
    }
  }

  override fun getArtists(
    accessToken: AccessToken,
    artistIds: List<String>,
  ): Either<DomainError, List<AppArtist>> {
    if (artistIds.isEmpty()) return emptyList<AppArtist>().right()
    try {
      SpotifyApiAuthContext.set(accessToken)
      val resolved = mutableListOf<AppArtist>()
      var lastError: DomainError? = null
      // Each chunk is resolved independently, so a failure fetching one chunk (e.g. a transient error or rate
      // limit hit partway through a large batch) does not discard artists already resolved from earlier chunks.
      artistIds.chunked(SEVERAL_ARTISTS_PAGE_SIZE).forEach { chunk ->
        throttler.throttle(DomainOutboxPartition.ToSpotifyCatalog.key)
        try {
          val response = httpMetrics.timed("/v1/artists") { apiClient.getSeveralArtists(chunk.joinToString(",")) }
          resolved += response.artists.filterNotNull().map { parseArtist(it) }
        } catch (e: SpotifyRateLimitException) {
          lastError = SpotifyRateLimitError(e.retryAfterSeconds.seconds)
        } catch (e: SpotifyApiException) {
          logger.error { "Spotify several-artists fetch failed for ${chunk.size} id(s): ${e.statusCode}" }
          lastError = SyncError.ARTIST_DETAILS_FETCH_FAILED
        } catch (e: Exception) {
          logger.error(e) { "Unexpected error fetching several artists (${chunk.size} id(s))" }
          lastError = SyncError.ARTIST_DETAILS_FETCH_FAILED
        }
      }
      val error = lastError
      return if (resolved.isNotEmpty() || error == null) resolved.right() else error.left()
    } finally {
      SpotifyApiAuthContext.clear()
    }
  }

  override fun getAlbum(
    accessToken: AccessToken,
    albumId: String,
  ): Either<DomainError, AlbumSyncResult> {
    return try {
      SpotifyApiAuthContext.set(accessToken)
      throttler.throttle(DomainOutboxPartition.ToSpotifyCatalog.key)
      val albumResponse = httpMetrics.timed("/v1/albums/{id}") { apiClient.getAlbum(albumId) }
      val appAlbum = parseAlbum(albumResponse, albumId)
      val allTracks = albumResponse.tracks.items.toMutableList()
      var nextOffset: Int? = albumResponse.tracks.next?.queryParamInt("offset")
      while (nextOffset != null) {
        throttler.throttle(DomainOutboxPartition.ToSpotifyCatalog.key)
        val nextPage = httpMetrics.timed("/v1/albums/{id}/tracks") {
          apiClient.getAlbumTracks(albumId, ALBUM_TRACKS_PAGE_SIZE, nextOffset)
        }
        allTracks.addAll(nextPage.items)
        nextOffset = nextPage.next?.queryParamInt("offset")
      }
      val parsedTracks = allTracks.mapNotNull { parseAlbumTrack(it, appAlbum, appAlbum.lastSync) }
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
      logger.error { "Spotify album fetch failed for $albumId: status=${e.statusCode}, body=${e.body.take(500)}" }
      SyncError.TRACK_DETAILS_FETCH_FAILED.left()
    } catch (e: Exception) {
      logger.error(e) {
        "Unexpected error fetching album tracks for album $albumId: ${e::class.simpleName}: ${e.message}"
      }
      SyncError.TRACK_DETAILS_FETCH_FAILED.left()
    } finally {
      SpotifyApiAuthContext.clear()
    }
  }

  override fun getAlbumTracks(
    accessToken: AccessToken,
    album: AppAlbum,
  ): Either<DomainError, List<AppTrack>> {
    val albumId = album.id.value
    return try {
      SpotifyApiAuthContext.set(accessToken)
      val lastSync = Clock.System.now()
      val allTracks = mutableListOf<SimplifiedTrackObject>()
      var offset: Int? = 0
      while (offset != null) {
        throttler.throttle(DomainOutboxPartition.ToSpotifyCatalog.key)
        val page = httpMetrics.timed("/v1/albums/{id}/tracks") {
          apiClient.getAlbumTracks(albumId, ALBUM_TRACKS_PAGE_SIZE, offset)
        }
        allTracks.addAll(page.items)
        offset = page.next?.queryParamInt("offset")
      }
      val parsedTracks = allTracks.mapNotNull { parseAlbumTrack(it, album, lastSync) }
      val droppedCount = allTracks.size - parsedTracks.size
      if (droppedCount > 0) {
        logger.warn {
          "Album $albumId: dropped $droppedCount track(s) without id or primary artist" +
            " (fetched ${allTracks.size}, album reports ${album.totalTracks} total)"
        }
      }
      parsedTracks.right()
    } catch (e: SpotifyRateLimitException) {
      SpotifyRateLimitError(e.retryAfterSeconds.seconds).left()
    } catch (e: SpotifyApiException) {
      logger.error { "Spotify album tracks fetch failed for $albumId: status=${e.statusCode}, body=${e.body.take(500)}" }
      SyncError.TRACK_DETAILS_FETCH_FAILED.left()
    } catch (e: Exception) {
      logger.error(e) {
        "Unexpected error fetching album tracks for album $albumId: ${e::class.simpleName}: ${e.message}"
      }
      SyncError.TRACK_DETAILS_FETCH_FAILED.left()
    } finally {
      SpotifyApiAuthContext.clear()
    }
  }

  override fun getArtistAlbumsPage(
    accessToken: AccessToken,
    artistId: String,
    nextUrl: String?,
  ): Either<DomainError, ArtistAlbumsPage> {
    return try {
      SpotifyApiAuthContext.set(accessToken)
      val offset = nextUrl?.queryParamInt("offset")
      throttler.throttle(DomainOutboxPartition.ToSpotifyCatalog.key)
      val page = httpMetrics.timed("/v1/artists/{id}/albums") {
        apiClient.getArtistAlbums(artistId, ARTIST_ALBUMS_PAGE_SIZE, offset, ARTIST_ALBUMS_INCLUDE_GROUPS)
      }
      ArtistAlbumsPage(albums = page.items.mapNotNull { parseDiscographyAlbum(it) }, nextUrl = page.next).right()
    } catch (e: SpotifyRateLimitException) {
      SpotifyRateLimitError(e.retryAfterSeconds.seconds).left()
    } catch (e: SpotifyApiException) {
      logger.error { "Spotify artist albums fetch failed for $artistId: ${e.statusCode}" }
      SyncError.ARTIST_DETAILS_FETCH_FAILED.left()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error fetching album ids for artist $artistId" }
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

  private fun parseAlbum(album: AlbumObject, fallbackAlbumId: String): AppAlbum =
    buildAppAlbum(
      albumId = album.id ?: fallbackAlbumId,
      name = album.name,
      images = album.images,
      releaseDate = album.releaseDate,
      releaseDatePrecision = album.releaseDatePrecision,
      albumType = album.albumType,
      totalTracks = album.totalTracks,
      artists = album.artists,
    )

  private fun parseDiscographyAlbum(album: ArtistDiscographyAlbumObject): AppAlbum? {
    val albumId = album.id ?: return null
    return buildAppAlbum(
      albumId = albumId,
      name = album.name,
      images = album.images,
      releaseDate = album.releaseDate,
      releaseDatePrecision = album.releaseDatePrecision,
      albumType = album.albumType,
      totalTracks = album.totalTracks,
      artists = album.artists,
    )
  }

  private fun buildAppAlbum(
    albumId: String,
    name: String?,
    images: List<ImageObject>,
    releaseDate: String?,
    releaseDatePrecision: String?,
    albumType: String?,
    totalTracks: Int?,
    artists: List<SimplifiedArtistObject>,
  ): AppAlbum =
    AppAlbum(
      id = AlbumId(albumId),
      totalTracks = totalTracks,
      title = name,
      imageLink = images.firstOrNull()?.url,
      releaseDate = releaseDate,
      releaseDatePrecision = releaseDatePrecision ?: "",
      type = albumType ?: "",
      artistId = artists.firstOrNull()?.id?.let { ArtistId(it) },
      artistName = artists.firstOrNull()?.name,
      additionalArtistIds = artists.additionalItems { id?.let { ArtistId(it) } }?.filterNotNull(),
      additionalArtistNames = artists.additionalItems { name }?.filterNotNull(),
      lastSync = Clock.System.now(),
    )

  private fun parseAlbumTrack(track: SimplifiedTrackObject, album: AppAlbum, lastSync: Instant): AppTrack? {
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
      lastSync = lastSync,
    )
  }

  private fun <T, R> List<T>.additionalItems(extractor: T.() -> R): List<R>? =
    if (size <= 1) null else (1 until size).map { get(it).extractor() }

  companion object : KLogging() {
    private const val ALBUM_TRACKS_PAGE_SIZE = 50
    private const val ARTIST_ALBUMS_PAGE_SIZE = 10
    private const val ARTIST_ALBUMS_INCLUDE_GROUPS = "album,single"
    private const val SEVERAL_ARTISTS_PAGE_SIZE = 50
  }
}
