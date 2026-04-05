package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyPlaylistTrackObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyPlaylistTracksResponse
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyRemovePlaylistTracksRequest
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyRemoveTrackObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyUserPlaylistsResponse
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.PlaylistFixError
import de.chrgroth.spotify.control.domain.error.PlaylistSyncError
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTracksPage
import de.chrgroth.spotify.control.domain.model.playlist.SpotifyPlaylistItem
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.playlist.SpotifyPlaylistPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class SpotifyPlaylistAdapter(
  @param:ConfigProperty(name = "spotify.api.base-url")
  private val apiBaseUrl: String,
  private val httpMetrics: SpotifyHttpMetrics,
  private val throttler: SpotifyRequestThrottler,
) : SpotifyPlaylistPort {

  private val httpClient = HttpClient.newHttpClient()

  override fun getPlaylists(userId: UserId, accessToken: AccessToken): Either<DomainError, List<SpotifyPlaylistItem>> {
    return try {
      val items = mutableListOf<SpotifyPlaylistItem>()
      var nextUrl: String? = "$apiBaseUrl/v1/me/playlists?limit=50"
      while (nextUrl != null) {
        throttler.throttle(DomainOutboxPartition.ToSpotify.key)
        val request = HttpRequest.newBuilder()
          .uri(URI.create(nextUrl))
          .header("Authorization", "Bearer ${accessToken.value}")
          .GET()
          .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val errorResult = response.checkRateLimitOrError(logger, PlaylistSyncError.PLAYLIST_FETCH_FAILED)
        if (errorResult != null) return errorResult
        val playlistsResponse = spotifyJson.decodeFromString<SpotifyUserPlaylistsResponse>(response.body())
        items += playlistsResponse.items.map { playlist ->
          SpotifyPlaylistItem(
            id = playlist.id,
            name = playlist.name,
            snapshotId = playlist.snapshotId,
            ownerId = playlist.owner.id,
          )
        }
        nextUrl = playlistsResponse.next
      }
      items.right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during playlist fetch for user ${userId.value}" }
      PlaylistSyncError.PLAYLIST_FETCH_FAILED.left()
    }
  }

  override fun getPlaylistTracks(userId: UserId, accessToken: AccessToken, playlistId: String): Either<DomainError, Playlist> {
    return try {
      val tracks = mutableListOf<PlaylistTrack>()
      var nextUrl: String? = "$apiBaseUrl/v1/playlists/$playlistId/items?limit=50"
      while (nextUrl != null) {
        throttler.throttle(DomainOutboxPartition.ToSpotify.key)
        val request = HttpRequest.newBuilder()
          .uri(URI.create(nextUrl))
          .header("Authorization", "Bearer ${accessToken.value}")
          .GET()
          .build()
        val response = httpMetrics.timed("/v1/playlists/{id}/items") {
          httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }
        val errorResult = response.checkRateLimitOrError(logger, PlaylistSyncError.PLAYLIST_TRACKS_FETCH_FAILED)
        if (errorResult != null) return errorResult
        val tracksResponse = spotifyJson.decodeFromString<SpotifyPlaylistTracksResponse>(response.body())
        tracks += parsePlaylistTracks(tracksResponse.items)
        nextUrl = tracksResponse.next
      }
      Playlist(
        spotifyPlaylistId = playlistId,
        tracks = tracks,
      ).right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during playlist tracks fetch for playlist $playlistId (user ${userId.value})" }
      PlaylistSyncError.PLAYLIST_TRACKS_FETCH_FAILED.left()
    }
  }

  override fun getPlaylistTracksPage(userId: UserId, accessToken: AccessToken, playlistId: String, pageUrl: String?): Either<DomainError, PlaylistTracksPage> {
    return try {
      val url = pageUrl ?: "$apiBaseUrl/v1/playlists/$playlistId/items?limit=50"
      throttler.throttle(DomainOutboxPartition.ToSpotify.key)
      val request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Authorization", "Bearer ${accessToken.value}")
        .GET()
        .build()
      val response = httpMetrics.timed("/v1/playlists/{id}/items") {
        httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      }
      val errorResult = response.checkRateLimitOrError(logger, PlaylistSyncError.PLAYLIST_TRACKS_FETCH_FAILED)
      if (errorResult != null) return errorResult
      val tracksResponse = spotifyJson.decodeFromString<SpotifyPlaylistTracksResponse>(response.body())
      PlaylistTracksPage(
        snapshotId = tracksResponse.snapshotId ?: "",
        tracks = parsePlaylistTracks(tracksResponse.items),
        nextUrl = tracksResponse.next,
      ).right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during playlist tracks page fetch for playlist $playlistId (user ${userId.value})" }
      PlaylistSyncError.PLAYLIST_TRACKS_FETCH_FAILED.left()
    }
  }

  private fun parsePlaylistTracks(items: List<SpotifyPlaylistTrackObject>): List<PlaylistTrack> =
    items.mapNotNull { item ->
      val track = item.item ?: return@mapNotNull null
      if (track.type != "track") {
        logger.info { "Ignoring non-track playlist item of type '${track.type}'" }
        return@mapNotNull null
      }
      val albumId = track.album?.id
      if (albumId == null) {
        logger.error { "Ignoring track ${track.id} without albumId in playlist" }
        return@mapNotNull null
      }
      PlaylistTrack(
        trackId = TrackId(track.id),
        artistIds = track.artists.map { ArtistId(it.id) },
        albumId = AlbumId(albumId),
      )
    }

  override fun removePlaylistTracks(
    userId: UserId,
    accessToken: AccessToken,
    playlistId: String,
    positionsByTrackId: Map<String, List<Int>>,
  ): Either<DomainError, Unit> {
    return try {
      val allTracks = positionsByTrackId.entries.map { (trackId, positions) ->
        SpotifyRemoveTrackObject(uri = "spotify:track:$trackId", positions = positions)
      }
      allTracks.chunked(REMOVE_TRACKS_BATCH_SIZE).forEach { batch ->
        throttler.throttle(DomainOutboxPartition.ToSpotify.key)
        val requestBody = spotifyJson.encodeToString(SpotifyRemovePlaylistTracksRequest(tracks = batch))
        val request = HttpRequest.newBuilder()
          .uri(URI.create("$apiBaseUrl/v1/playlists/$playlistId/tracks"))
          .header("Authorization", "Bearer ${accessToken.value}")
          .header("Content-Type", "application/json")
          .method("DELETE", HttpRequest.BodyPublishers.ofString(requestBody))
          .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val errorResult = response.checkRateLimitOrError(logger, PlaylistFixError.FIX_FAILED)
        if (errorResult != null) return errorResult
      }
      Unit.right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during track removal from playlist $playlistId (user ${userId.value})" }
      PlaylistFixError.FIX_FAILED.left()
    }
  }

  companion object : KLogging() {
    private const val REMOVE_TRACKS_BATCH_SIZE = 100
  }
}
