package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.adapter.out.spotify.model.PagingPlaylistObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.PagingPlaylistTrackObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.PlaylistTrackObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyAddPlaylistTracksRequest
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyRemovePlaylistTrackAtPositionRequest
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyRemovePlaylistTracksRequest
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyRemoveTrackAtPositionObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyRemoveTrackObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.TrackObject
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.PlaylistFixError
import de.chrgroth.spotify.control.domain.error.PlaylistSyncError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTracksPage
import de.chrgroth.spotify.control.domain.model.playlist.SpotifyPlaylistItem
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.playlist.SpotifyPlaylistPort
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.serialization.json.JsonElement
import mu.KLogging
import org.eclipse.microprofile.rest.client.inject.RestClient
import kotlin.time.Duration.Companion.seconds

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class SpotifyPlaylistAdapter(
  private val httpMetrics: SpotifyHttpMetrics,
  private val throttler: SpotifyRequestThrottler,
  @param:RestClient private val apiClient: SpotifyApiRestClient,
) : SpotifyPlaylistPort {

  override fun getPlaylists(userId: UserId, accessToken: AccessToken): Either<DomainError, List<SpotifyPlaylistItem>> {
    return try {
      SpotifyApiAuthContext.set(accessToken)
      val items = mutableListOf<SpotifyPlaylistItem>()
      var offset: Int? = null
      var hasNext = true
      while (hasNext) {
        throttler.throttle(DomainOutboxPartition.ToSpotify.key)
        val playlistsResponse = apiClient.getUserPlaylists(PLAYLISTS_PAGE_SIZE, offset)
        items += playlistsResponse.items.map { playlist ->
          SpotifyPlaylistItem(
            id = playlist.id ?: "",
            name = playlist.name ?: "",
            snapshotId = playlist.snapshotId ?: "",
            ownerId = playlist.owner?.id ?: "",
          )
        }
        val nextUrl = playlistsResponse.next
        if (nextUrl == null) {
          hasNext = false
        } else {
          offset = nextUrl.queryParamInt("offset")
          if (offset == null) hasNext = false
        }
      }
      items.right()
    } catch (e: SpotifyRateLimitException) {
      SpotifyRateLimitError(e.retryAfterSeconds.seconds).left()
    } catch (e: SpotifyApiException) {
      logger.error { "Spotify playlist fetch failed: ${e.statusCode}" }
      PlaylistSyncError.PLAYLIST_FETCH_FAILED.left()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during playlist fetch for user ${userId.value}" }
      PlaylistSyncError.PLAYLIST_FETCH_FAILED.left()
    } finally {
      SpotifyApiAuthContext.clear()
    }
  }

  override fun getPlaylistTracks(userId: UserId, accessToken: AccessToken, playlistId: String): Either<DomainError, Playlist> {
    return try {
      SpotifyApiAuthContext.set(accessToken)
      val tracks = mutableListOf<PlaylistTrack>()
      var offset: Int? = null
      var hasNext = true
      while (hasNext) {
        throttler.throttle(DomainOutboxPartition.ToSpotify.key)
        val tracksResponse = httpMetrics.timed("/v1/playlists/{id}/items") {
          apiClient.getPlaylistItems(playlistId, PLAYLIST_TRACKS_PAGE_SIZE, offset)
        }
        tracks += parsePlaylistTracks(tracksResponse.items)
        val nextUrl = tracksResponse.next
        if (nextUrl == null) {
          hasNext = false
        } else {
          offset = nextUrl.queryParamInt("offset")
          if (offset == null) hasNext = false
        }
      }
      Playlist(
        spotifyPlaylistId = playlistId,
        tracks = tracks,
      ).right()
    } catch (e: SpotifyRateLimitException) {
      SpotifyRateLimitError(e.retryAfterSeconds.seconds).left()
    } catch (e: SpotifyApiException) {
      logger.error { "Spotify playlist tracks fetch failed for $playlistId: ${e.statusCode}" }
      PlaylistSyncError.PLAYLIST_TRACKS_FETCH_FAILED.left()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during playlist tracks fetch for playlist $playlistId (user ${userId.value})" }
      PlaylistSyncError.PLAYLIST_TRACKS_FETCH_FAILED.left()
    } finally {
      SpotifyApiAuthContext.clear()
    }
  }

  override fun getPlaylistTracksPage(userId: UserId, accessToken: AccessToken, playlistId: String, pageUrl: String?): Either<DomainError, PlaylistTracksPage> {
    return try {
      SpotifyApiAuthContext.set(accessToken)
      val offset = pageUrl?.queryParamInt("offset")
      throttler.throttle(DomainOutboxPartition.ToSpotify.key)
      val tracksResponse = httpMetrics.timed("/v1/playlists/{id}/items") {
        apiClient.getPlaylistItems(playlistId, PLAYLIST_TRACKS_PAGE_SIZE, offset)
      }
      PlaylistTracksPage(
        snapshotId = tracksResponse.snapshotId ?: "",
        tracks = parsePlaylistTracks(tracksResponse.items),
        nextUrl = tracksResponse.next,
      ).right()
    } catch (e: SpotifyRateLimitException) {
      SpotifyRateLimitError(e.retryAfterSeconds.seconds).left()
    } catch (e: SpotifyApiException) {
      logger.error { "Spotify playlist tracks page fetch failed for $playlistId: ${e.statusCode}" }
      PlaylistSyncError.PLAYLIST_TRACKS_FETCH_FAILED.left()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during playlist tracks page fetch for playlist $playlistId (user ${userId.value})" }
      PlaylistSyncError.PLAYLIST_TRACKS_FETCH_FAILED.left()
    } finally {
      SpotifyApiAuthContext.clear()
    }
  }

  private fun parsePlaylistTracks(items: List<PlaylistTrackObject?>): List<PlaylistTrack> =
    items.mapNotNull { item ->
      if (item == null) return@mapNotNull null
      val itemElement = item.item ?: return@mapNotNull null
      val track = decodeTrack(itemElement) ?: return@mapNotNull null
      if (track.type != TrackObject.Type.TRACK) {
        logger.info { "Ignoring non-track playlist item of type '${track.type}'" }
        return@mapNotNull null
      }
      if (track.id == null) {
        logger.info { "Ignoring local track '${track.name}' without id in playlist" }
        return@mapNotNull null
      }
      val albumId = track.album?.id
      if (albumId == null) {
        logger.warn { "Track ${track.id} has no albumId in playlist, saving track without album" }
      }
      val artistIds = (track.artists ?: emptyList()).mapNotNull { artist ->
        if (artist.id == null) {
          logger.warn { "Artist '${artist.name}' has no id in track ${track.id}, skipping artist" }
          null
        } else {
          ArtistId(artist.id)
        }
      }
      PlaylistTrack(
        trackId = TrackId(track.id),
        artistIds = artistIds,
        albumId = albumId?.let { AlbumId(it) },
      )
    }

  override fun removePlaylistTracks(
    userId: UserId,
    accessToken: AccessToken,
    playlistId: String,
    trackIds: List<String>,
  ): Either<DomainError, Unit> {
    return try {
      SpotifyApiAuthContext.set(accessToken)
      val allItems = trackIds.map { trackId -> SpotifyRemoveTrackObject(uri = "spotify:track:$trackId") }
      allItems.chunked(REMOVE_TRACKS_BATCH_SIZE).forEach { batch ->
        throttler.throttle(DomainOutboxPartition.ToSpotify.key)
        val requestBody = spotifyJson.encodeToString(SpotifyRemovePlaylistTracksRequest(items = batch))
        apiClient.removePlaylistItems(playlistId, requestBody)
      }
      Unit.right()
    } catch (e: SpotifyRateLimitException) {
      SpotifyRateLimitError(e.retryAfterSeconds.seconds).left()
    } catch (e: SpotifyApiException) {
      logger.error { "Spotify track removal failed for playlist $playlistId: ${e.statusCode}" }
      PlaylistFixError.FIX_FAILED.left()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during track removal from playlist $playlistId (user ${userId.value})" }
      PlaylistFixError.FIX_FAILED.left()
    } finally {
      SpotifyApiAuthContext.clear()
    }
  }

  override fun addPlaylistTracks(
    userId: UserId,
    accessToken: AccessToken,
    playlistId: String,
    trackIds: List<String>,
  ): Either<DomainError, Unit> {
    return try {
      SpotifyApiAuthContext.set(accessToken)
      val uris = trackIds.map { "spotify:track:$it" }
      uris.chunked(ADD_TRACKS_BATCH_SIZE).forEach { batch ->
        throttler.throttle(DomainOutboxPartition.ToSpotify.key)
        val requestBody = spotifyJson.encodeToString(SpotifyAddPlaylistTracksRequest(uris = batch))
        apiClient.addPlaylistItems(playlistId, requestBody)
      }
      Unit.right()
    } catch (e: SpotifyRateLimitException) {
      SpotifyRateLimitError(e.retryAfterSeconds.seconds).left()
    } catch (e: SpotifyApiException) {
      logger.error { "Spotify track addition failed for playlist $playlistId: ${e.statusCode}" }
      PlaylistFixError.FIX_FAILED.left()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during track addition to playlist $playlistId (user ${userId.value})" }
      PlaylistFixError.FIX_FAILED.left()
    } finally {
      SpotifyApiAuthContext.clear()
    }
  }

  override fun replacePlaylistTrack(
    userId: UserId,
    accessToken: AccessToken,
    playlistId: String,
    oldTrackId: String,
    newTrackId: String,
    position: Int,
  ): Either<DomainError, Unit> {
    return try {
      SpotifyApiAuthContext.set(accessToken)
      throttler.throttle(DomainOutboxPartition.ToSpotify.key)
      val removeBody = spotifyJson.encodeToString(
        SpotifyRemovePlaylistTrackAtPositionRequest(
          items = listOf(SpotifyRemoveTrackAtPositionObject(uri = "spotify:track:$oldTrackId", positions = listOf(position))),
        ),
      )
      apiClient.removePlaylistItemsByPosition(playlistId, removeBody)

      throttler.throttle(DomainOutboxPartition.ToSpotify.key)
      val addBody = spotifyJson.encodeToString(SpotifyAddPlaylistTracksRequest(uris = listOf("spotify:track:$newTrackId"), position = position))
      apiClient.addPlaylistItemsToTracks(playlistId, addBody)

      Unit.right()
    } catch (e: SpotifyRateLimitException) {
      SpotifyRateLimitError(e.retryAfterSeconds.seconds).left()
    } catch (e: SpotifyApiException) {
      logger.error { "Spotify track replacement failed for playlist $playlistId: ${e.statusCode}" }
      PlaylistFixError.FIX_FAILED.left()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during track replacement in playlist $playlistId (user ${userId.value})" }
      PlaylistFixError.FIX_FAILED.left()
    } finally {
      SpotifyApiAuthContext.clear()
    }
  }

  private fun decodeTrack(element: JsonElement): TrackObject? =
    runCatching { spotifyJson.decodeFromJsonElement(TrackObject.serializer(), element) }.getOrNull()

  companion object : KLogging() {
    private const val PLAYLISTS_PAGE_SIZE = 50
    private const val PLAYLIST_TRACKS_PAGE_SIZE = 50
    private const val REMOVE_TRACKS_BATCH_SIZE = 100
    private const val ADD_TRACKS_BATCH_SIZE = 100
  }
}
