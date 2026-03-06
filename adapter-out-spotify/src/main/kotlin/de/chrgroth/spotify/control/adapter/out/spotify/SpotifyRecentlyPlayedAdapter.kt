package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import de.chrgroth.spotify.control.adapter.out.spotify.api.model.CursorPagingPlayHistoryObject
import de.chrgroth.spotify.control.adapter.out.spotify.api.model.PlayHistoryObject
import de.chrgroth.spotify.control.adapter.out.spotify.api.model.TrackObject
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.PlaybackError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.SpotifyRecentlyPlayedPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Instant

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class SpotifyRecentlyPlayedAdapter(
    @param:ConfigProperty(name = "spotify.api.base-url", defaultValue = "https://api.spotify.com")
    private val apiBaseUrl: String,
    private val httpMetrics: SpotifyHttpMetrics,
) : SpotifyRecentlyPlayedPort {

    private val httpClient = HttpClient.newHttpClient()
    private val objectMapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
    }

    override fun getRecentlyPlayed(userId: UserId, accessToken: AccessToken, after: Instant?): Either<DomainError, List<RecentlyPlayedItem>> {
        return try {
            val allItems = mutableListOf<RecentlyPlayedItem>()
            val afterParam = after?.let { "&after=${it.toEpochMilliseconds()}" } ?: ""
            var nextUrl: String? = "$apiBaseUrl/v1/me/player/recently-played?limit=50$afterParam"
            while (nextUrl != null) {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(nextUrl))
                    .header("Authorization", "Bearer ${accessToken.value}")
                    .GET()
                    .build()
                val response = httpMetrics.timed(request.uri()) {
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                }
                val errorResult = response.checkRateLimitOrError(logger, PlaybackError.RECENTLY_PLAYED_FETCH_FAILED)
                if (errorResult != null) return errorResult
                val page = objectMapper.readValue(response.body(), CursorPagingPlayHistoryObject::class.java)
                page.items?.mapNotNullTo(allItems) { mapHistoryItem(userId, it) }
                nextUrl = page.next
            }
            allItems.right()
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during recently played fetch" }
            PlaybackError.RECENTLY_PLAYED_FETCH_FAILED.left()
        }
    }

    private fun mapHistoryItem(
        userId: UserId,
        historyItem: PlayHistoryObject,
    ): RecentlyPlayedItem? {
        val track = historyItem.track ?: return null
        return when {
            track.type != TrackObject.Type.TRACK -> {
                logger.info { "Ignoring non-track playback event of type '${track.type?.value}'" }
                null
            }
            track.isLocal == true -> {
                logger.info { "Ignoring local track '${track.name}'" }
                null
            }
            else -> RecentlyPlayedItem(
                spotifyUserId = userId,
                trackId = track.id ?: "",
                trackName = track.name ?: "",
                artistIds = track.artists?.mapNotNull { it.id } ?: emptyList(),
                artistNames = track.artists?.mapNotNull { it.name } ?: emptyList(),
                playedAt = Instant.parse(historyItem.playedAt ?: ""),
            )
        }
    }

    companion object : KLogging()
}
