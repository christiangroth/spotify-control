package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.EnrichmentError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.AppAlbum
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.SpotifyAlbumDetailsPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class SpotifyAlbumDetailsAdapter(
    @param:ConfigProperty(name = "spotify.api.base-url")
    private val apiBaseUrl: String,
    private val httpMetrics: SpotifyHttpMetrics,
    private val throttler: SpotifyRequestThrottler,
) : SpotifyAlbumDetailsPort {

    private val httpClient = HttpClient.newHttpClient()
    private val objectMapper = ObjectMapper()

    override fun getAlbum(
        userId: UserId,
        accessToken: AccessToken,
        albumId: String,
    ): Either<DomainError, AppAlbum?> {
        return try {
            throttler.throttle(DomainOutboxPartition.ToSpotify.key)
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$apiBaseUrl/v1/albums/$albumId"))
                .header("Authorization", "Bearer ${accessToken.value}")
                .GET()
                .build()
            val response = httpMetrics.timed(request.uri()) {
                httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            }
            val errorResult = response.checkRateLimitOrError(logger, EnrichmentError.ALBUM_DETAILS_FETCH_FAILED)
            if (errorResult != null) return errorResult
            parseAlbumDetails(objectMapper.readTree(response.body())).right()
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error fetching album details for album $albumId (user ${userId.value})" }
            EnrichmentError.ALBUM_DETAILS_FETCH_FAILED.left()
        }
    }

    private fun parseAlbumDetails(json: JsonNode): AppAlbum? {
        if (json.isNull || !json.has("id")) return null
        return AppAlbum(
            albumId = json.get("id").asText(),
            albumTitle = json.get("name")?.asText(),
            imageLink = json.get("images")?.firstOrNull()?.get("url")?.asText(),
            genres = json.get("genres")?.map { it.asText() } ?: emptyList(),
            artistId = json.get("artists")?.firstOrNull()?.get("id")?.asText(),
        )
    }

    companion object : KLogging()
}
