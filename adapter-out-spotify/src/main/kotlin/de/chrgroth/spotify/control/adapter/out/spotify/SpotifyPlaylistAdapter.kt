package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.PlaylistSyncError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.Playlist
import de.chrgroth.spotify.control.domain.model.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.SpotifyPlaylistItem
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.SpotifyPlaylistPort
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
    private val objectMapper = ObjectMapper()

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
                val json: JsonNode = objectMapper.readTree(response.body())
                json.get("items")?.forEach { item ->
                    items.add(
                        SpotifyPlaylistItem(
                            id = item.get("id").asText(),
                            name = item.get("name").asText(),
                            snapshotId = item.get("snapshot_id").asText(),
                            ownerId = item.get("owner").get("id").asText(),
                        ),
                    )
                }
                nextUrl = json.get("next")?.takeIf { !it.isNull }?.asText()
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
      var snapshotId: String? = null
      var nextUrl: String? = "$apiBaseUrl/v1/playlists/$playlistId/items?limit=50"
      while (nextUrl != null) {
        throttler.throttle(DomainOutboxPartition.ToSpotify.key)
        val request = HttpRequest.newBuilder()
          .uri(URI.create(nextUrl))
          .header("Authorization", "Bearer ${accessToken.value}")
          .GET()
          .build()
        val response = httpMetrics.timed(request.uri()) {
          httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }
        val errorResult = response.checkRateLimitOrError(logger, PlaylistSyncError.PLAYLIST_TRACKS_FETCH_FAILED)
        if (errorResult != null) return errorResult
        val responseBody = response.body()
        val json: JsonNode = objectMapper.readTree(responseBody)
        if (snapshotId == null) {
          snapshotId = json.get("snapshot_id")?.asText()
        }
        json.get("items")?.forEach { item ->
          logger.info { "Processing item: $item" }
          val track = item.get("item")?.takeIf { !it.isNull } ?: return@forEach
          val type = track.get("type")?.asText()
          if (type != "track") {
            logger.info { "Ignoring non-track playlist item of type '$type'" }
            return@forEach
          }
          tracks.add(
            PlaylistTrack(
              trackId = track.get("id").asText(),
              trackName = track.get("name").asText(),
              artistIds = track.get("artists").map { it.get("id").asText() },
              artistNames = track.get("artists").map { it.get("name").asText() },
            ),
          )
        }
        nextUrl = json.get("next")?.takeIf { !it.isNull }?.asText()
      }
      Playlist(
        spotifyPlaylistId = playlistId,
        snapshotId = snapshotId ?: "",
        tracks = tracks,
      ).right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during playlist tracks fetch for playlist $playlistId (user ${userId.value})" }
      PlaylistSyncError.PLAYLIST_TRACKS_FETCH_FAILED.left()
    }
  }

    companion object : KLogging()
}

