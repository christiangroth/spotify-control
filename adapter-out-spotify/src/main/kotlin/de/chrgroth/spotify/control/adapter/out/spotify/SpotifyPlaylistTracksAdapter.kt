package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import de.chrgroth.spotify.control.adapter.out.spotify.api.model.PagingPlaylistTrackObject
import de.chrgroth.spotify.control.adapter.out.spotify.api.model.TrackObject
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.PlaylistSyncError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.Playlist
import de.chrgroth.spotify.control.domain.model.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.SpotifyPlaylistTracksPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class SpotifyPlaylistTracksAdapter(
    @param:ConfigProperty(name = "spotify.api.base-url", defaultValue = "https://api.spotify.com")
    private val apiBaseUrl: String,
    private val httpMetrics: SpotifyHttpMetrics,
) : SpotifyPlaylistTracksPort {

    private val httpClient = HttpClient.newHttpClient()
    private val objectMapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
    }

    override fun getPlaylistTracks(userId: UserId, accessToken: AccessToken, playlistId: String): Either<DomainError, Playlist> {
        return try {
            val tracks = mutableListOf<PlaylistTrack>()
            var snapshotId: String? = null
            var nextUrl: String? = "$apiBaseUrl/v1/playlists/$playlistId/items?limit=50"
            while (nextUrl != null) {
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
                val page = objectMapper.readValue(response.body(), PagingPlaylistTrackObject::class.java)
                if (snapshotId == null) {
                    snapshotId = page.snapshotId
                }
                page.items.forEach { item ->
                    val track = item.item ?: return@forEach
                    val type = track.type
                    if (type != TrackObject.Type.TRACK) {
                        logger.info { "Ignoring non-track playlist item of type '${track.type?.value}'" }
                        return@forEach
                    }
                    tracks.add(
                        PlaylistTrack(
                            trackId = track.id ?: "",
                            trackName = track.name ?: "",
                            artistIds = track.artists?.mapNotNull { it.id } ?: emptyList(),
                            artistNames = track.artists?.mapNotNull { it.name } ?: emptyList(),
                        ),
                    )
                }
                nextUrl = page.next
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
