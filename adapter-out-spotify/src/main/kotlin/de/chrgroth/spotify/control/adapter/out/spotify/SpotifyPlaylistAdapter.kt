package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import de.chrgroth.spotify.control.adapter.out.spotify.api.model.PagingPlaylistObject
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.PlaylistSyncError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.SpotifyPlaylistItem
import de.chrgroth.spotify.control.domain.model.UserId
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
    @param:ConfigProperty(name = "spotify.api.base-url", defaultValue = "https://api.spotify.com")
    private val apiBaseUrl: String,
) : SpotifyPlaylistPort {

    private val httpClient = HttpClient.newHttpClient()
    private val objectMapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    override fun getPlaylists(userId: UserId, accessToken: AccessToken): Either<DomainError, List<SpotifyPlaylistItem>> {
        return try {
            val items = mutableListOf<SpotifyPlaylistItem>()
            var nextUrl: String? = "$apiBaseUrl/v1/me/playlists?limit=50"
            while (nextUrl != null) {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(nextUrl))
                    .header("Authorization", "Bearer ${accessToken.value}")
                    .GET()
                    .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                val errorResult = response.checkRateLimitOrError(logger, PlaylistSyncError.PLAYLIST_FETCH_FAILED)
                if (errorResult != null) return errorResult
                val page = objectMapper.readValue(response.body(), PagingPlaylistObject::class.java)
                page.items.forEach { playlist ->
                    items.add(
                        SpotifyPlaylistItem(
                            id = playlist.id ?: "",
                            name = playlist.name ?: "",
                            snapshotId = playlist.snapshotId ?: "",
                            ownerId = playlist.owner?.id ?: "",
                        ),
                    )
                }
                nextUrl = page.next
            }
            items.right()
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during playlist fetch for user ${userId.value}" }
            PlaylistSyncError.PLAYLIST_FETCH_FAILED.left()
        }
    }

    companion object : KLogging()
}
