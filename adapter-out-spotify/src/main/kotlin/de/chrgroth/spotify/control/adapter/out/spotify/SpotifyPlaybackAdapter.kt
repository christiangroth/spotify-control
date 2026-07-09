package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.adapter.out.spotify.model.CurrentlyPlayingContextObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.CursorPagingPlayHistoryObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.TrackObject
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.PlaybackError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.playback.SpotifyPlaybackPort
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.serialization.json.JsonElement
import mu.KLogging
import org.eclipse.microprofile.rest.client.inject.RestClient
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class SpotifyPlaybackAdapter(
  private val httpMetrics: SpotifyHttpMetrics,
  @param:RestClient private val apiClient: SpotifyApiRestClient,
) : SpotifyPlaybackPort {

  override fun getCurrentlyPlaying(accessToken: AccessToken): Either<DomainError, CurrentlyPlayingItem?> {
    return try {
      SpotifyApiAuthContext.set(accessToken)
      val currentlyPlaying = httpMetrics.timed("/v1/me/player/currently-playing") {
        apiClient.getCurrentlyPlaying()
      }
      parseCurrentlyPlayingItem(currentlyPlaying).right()
    } catch (e: SpotifyNoContentException) {
      logger.debug(e) { "No currently playing item (204 No Content)" }
      null.right()
    } catch (e: SpotifyRateLimitException) {
      SpotifyRateLimitError(e.retryAfterSeconds.seconds).left()
    } catch (e: SpotifyApiException) {
      logger.error { "Spotify currently playing fetch failed: ${e.statusCode}" }
      PlaybackError.CURRENTLY_PLAYING_FETCH_FAILED.left()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during currently playing fetch" }
      PlaybackError.CURRENTLY_PLAYING_FETCH_FAILED.left()
    } finally {
      SpotifyApiAuthContext.clear()
    }
  }

  private fun parseCurrentlyPlayingItem(response: CurrentlyPlayingContextObject): CurrentlyPlayingItem? {
    val track = response.item?.let { decodeTrack(it) } ?: return null
    val trackId = track.id
    if (track.type != TrackObject.Type.TRACK || track.isLocal == true || trackId == null) {
      return null
    }
    val progressMs = response.progressMs ?: 0L
    val observedAt = Clock.System.now()
    return CurrentlyPlayingItem(
      // stamped with the real user by PlaybackService, which resolves "the" current user
      spotifyUserId = PLACEHOLDER_USER_ID,
      trackId = TrackId(trackId),
      trackName = track.name ?: "",
      artistIds = track.artists?.mapNotNull { it.id?.let { id -> ArtistId(id) } } ?: emptyList(),
      artistNames = track.artists?.map { it.name ?: "" } ?: emptyList(),
      progressMs = progressMs,
      durationMs = track.durationMs?.toLong() ?: 0L,
      isPlaying = response.isPlaying,
      observedAt = observedAt,
      startTime = observedAt - progressMs.milliseconds,
      albumId = track.album?.id?.let { AlbumId(it) },
    )
  }

  override fun getRecentlyPlayed(accessToken: AccessToken, after: Instant?): Either<DomainError, List<RecentlyPlayedItem>> {
    return try {
      SpotifyApiAuthContext.set(accessToken)
      val allItems = mutableListOf<RecentlyPlayedItem>()
      var afterParam: Long? = after?.toEpochMilliseconds()
      var hasNext = true
      while (hasNext) {
        val recentlyPlayed = httpMetrics.timed("/v1/me/player/recently-played") {
          apiClient.getRecentlyPlayed(RECENTLY_PLAYED_LIMIT, afterParam)
        }
        recentlyPlayed.items?.mapNotNullTo(allItems) { item ->
          val track = item.track ?: return@mapNotNullTo null
          parseRecentlyPlayedItem(track, item.playedAt ?: return@mapNotNullTo null)
        }
        val nextUrl = recentlyPlayed.next
        if (nextUrl == null) {
          hasNext = false
        } else {
          afterParam = nextUrl.queryParamLong("after")
          if (afterParam == null) hasNext = false
        }
      }
      allItems.right()
    } catch (e: SpotifyRateLimitException) {
      SpotifyRateLimitError(e.retryAfterSeconds.seconds).left()
    } catch (e: SpotifyApiException) {
      logger.error { "Spotify recently played fetch failed: ${e.statusCode}" }
      PlaybackError.RECENTLY_PLAYED_FETCH_FAILED.left()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during recently played fetch" }
      PlaybackError.RECENTLY_PLAYED_FETCH_FAILED.left()
    } finally {
      SpotifyApiAuthContext.clear()
    }
  }

  private fun parseRecentlyPlayedItem(track: TrackObject, playedAt: String): RecentlyPlayedItem? {
    if (track.type != TrackObject.Type.TRACK) {
      return null
    }
    if (track.isLocal == true || track.id == null) {
      return null
    }
    val durationSeconds = track.durationMs?.let { it.toLong() / MS_PER_SECOND }
    val playedAtInstant = Instant.parse(playedAt)
    return RecentlyPlayedItem(
      // stamped with the real user by PlaybackService, which resolves "the" current user
      spotifyUserId = PLACEHOLDER_USER_ID,
      trackId = TrackId(track.id),
      trackName = track.name ?: "",
      artistIds = track.artists?.mapNotNull { it.id?.let { id -> ArtistId(id) } } ?: emptyList(),
      artistNames = track.artists?.map { it.name ?: "" } ?: emptyList(),
      playedAt = playedAtInstant,
      startTime = durationSeconds?.let { playedAtInstant - it.seconds },
      albumId = track.album?.id?.let { AlbumId(it) },
      durationSeconds = durationSeconds,
    )
  }

  private fun decodeTrack(element: JsonElement): TrackObject? =
    runCatching { spotifyJson.decodeFromJsonElement(TrackObject.serializer(), element) }.getOrNull()

  companion object : KLogging() {
    private const val MS_PER_SECOND = 1_000L
    private const val RECENTLY_PLAYED_LIMIT = 50
    private val PLACEHOLDER_USER_ID = UserId("")
  }
}
