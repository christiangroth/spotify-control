package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.adapter.out.spotify.model.CurrentlyPlayingContextObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.CursorPagingPlayHistoryObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.TrackObject
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.PlaybackError
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

  override fun getCurrentlyPlaying(userId: UserId, accessToken: AccessToken): Either<DomainError, CurrentlyPlayingItem?> {
    return try {
      val response = httpMetrics.timed("/v1/me/player/currently-playing") {
        apiClient.getCurrentlyPlaying("Bearer ${accessToken.value}")
      }
      if (response.status == HTTP_NO_CONTENT) {
        return null.right()
      }
      val errorResult = response.checkRateLimitOrError(logger, "/v1/me/player/currently-playing", PlaybackError.CURRENTLY_PLAYING_FETCH_FAILED)
      if (errorResult != null) return errorResult
      val currentlyPlaying = spotifyJson.decodeFromString<CurrentlyPlayingContextObject>(response.readEntity(String::class.java))
      parseCurrentlyPlayingItem(userId, currentlyPlaying).right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during currently playing fetch" }
      PlaybackError.CURRENTLY_PLAYING_FETCH_FAILED.left()
    }
  }

  private fun parseCurrentlyPlayingItem(userId: UserId, response: CurrentlyPlayingContextObject): CurrentlyPlayingItem? {
    val itemElement = response.item ?: return null
    val track = decodeTrack(itemElement) ?: return null
    if (track.type != TrackObject.Type.TRACK) {
      logger.info { "Ignoring non-track currently playing event of type '${track.type}'" }
      return null
    }
    if (track.isLocal == true || track.id == null) {
      logger.info { "Ignoring local or id-less currently playing track '${track.name}'" }
      return null
    }
    val progressMs = response.progressMs ?: 0L
    val observedAt = Clock.System.now()
    return CurrentlyPlayingItem(
      spotifyUserId = userId,
      trackId = TrackId(track.id),
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

  override fun getRecentlyPlayed(userId: UserId, accessToken: AccessToken, after: Instant?): Either<DomainError, List<RecentlyPlayedItem>> {
    return try {
      val allItems = mutableListOf<RecentlyPlayedItem>()
      var afterParam: Long? = after?.toEpochMilliseconds()
      var hasNext = true
      while (hasNext) {
        val response = httpMetrics.timed("/v1/me/player/recently-played") {
          apiClient.getRecentlyPlayed("Bearer ${accessToken.value}", 50, afterParam)
        }
        val errorResult = response.checkRateLimitOrError(logger, "/v1/me/player/recently-played", PlaybackError.RECENTLY_PLAYED_FETCH_FAILED)
        if (errorResult != null) return errorResult
        val recentlyPlayed = spotifyJson.decodeFromString<CursorPagingPlayHistoryObject>(response.readEntity(String::class.java))
        recentlyPlayed.items?.mapNotNullTo(allItems) { item ->
          val track = item.track ?: return@mapNotNullTo null
          parseRecentlyPlayedItem(userId, track, item.playedAt ?: return@mapNotNullTo null)
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
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during recently played fetch" }
      PlaybackError.RECENTLY_PLAYED_FETCH_FAILED.left()
    }
  }

  private fun parseRecentlyPlayedItem(userId: UserId, track: TrackObject, playedAt: String): RecentlyPlayedItem? {
    if (track.type != TrackObject.Type.TRACK) {
      logger.info { "Ignoring non-track playback event of type '${track.type}'" }
      return null
    }
    if (track.isLocal == true || track.id == null) {
      logger.info { "Ignoring local or id-less recently played track '${track.name}'" }
      return null
    }
    val durationSeconds = track.durationMs?.let { it.toLong() / MS_PER_SECOND }
    val playedAtInstant = Instant.parse(playedAt)
    return RecentlyPlayedItem(
      spotifyUserId = userId,
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
  }
}
