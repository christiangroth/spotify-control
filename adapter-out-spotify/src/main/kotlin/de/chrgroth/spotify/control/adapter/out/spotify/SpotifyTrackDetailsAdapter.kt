package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.EnrichmentError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.SpotifyTrackDetails
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.SpotifyTrackDetailsPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class SpotifyTrackDetailsAdapter(
    @param:ConfigProperty(name = "spotify.api.base-url", defaultValue = "https://api.spotify.com")
    private val apiBaseUrl: String,
    private val httpMetrics: SpotifyHttpMetrics,
) : SpotifyTrackDetailsPort {

    private val httpClient = HttpClient.newHttpClient()
    private val objectMapper = ObjectMapper()

    override fun getTracks(
        userId: UserId,
        accessToken: AccessToken,
        trackIds: List<String>,
    ): Either<DomainError, List<SpotifyTrackDetails>> {
        if (trackIds.isEmpty()) return emptyList<SpotifyTrackDetails>().right()
        return try {
            val results = mutableListOf<SpotifyTrackDetails>()
            trackIds.chunked(BATCH_SIZE).forEach { batch ->
                val ids = batch.joinToString(",")
                val request = HttpRequest.newBuilder()
                    .uri(URI.create("$apiBaseUrl/v1/tracks?ids=$ids"))
                    .header("Authorization", "Bearer ${accessToken.value}")
                    .GET()
                    .build()
                val response = httpMetrics.timed(request.uri()) {
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                }
                val errorResult = response.checkRateLimitOrError(logger, EnrichmentError.TRACK_DETAILS_FETCH_FAILED)
                if (errorResult != null) return errorResult
                val json: JsonNode = objectMapper.readTree(response.body())
                json.get("tracks")?.mapNotNullTo(results) { track ->
                    if (track.isNull) return@mapNotNullTo null
                    val album = track.get("album")
                    SpotifyTrackDetails(
                        trackId = track.get("id").asText(),
                        albumId = album?.get("id")?.asText() ?: return@mapNotNullTo null,
                        albumTitle = album.get("name")?.asText(),
                        albumImageUrl = album.get("images")
                            ?.firstOrNull()
                            ?.get("url")
                            ?.asText(),
                    )
                }
            }
            results.right()
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error fetching track details for user ${userId.value}" }
            EnrichmentError.TRACK_DETAILS_FETCH_FAILED.left()
        }
    }

    companion object : KLogging() {
        private const val BATCH_SIZE = 50
    }
}
