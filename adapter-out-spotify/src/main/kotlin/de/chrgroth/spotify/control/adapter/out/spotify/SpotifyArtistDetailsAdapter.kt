package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.EnrichmentError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.SpotifyArtistDetails
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.SpotifyArtistDetailsPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class SpotifyArtistDetailsAdapter(
    @param:ConfigProperty(name = "spotify.api.base-url", defaultValue = "https://api.spotify.com")
    private val apiBaseUrl: String,
    private val httpMetrics: SpotifyHttpMetrics,
) : SpotifyArtistDetailsPort {

    private val httpClient = HttpClient.newHttpClient()
    private val objectMapper = ObjectMapper()

    override fun getArtists(
        userId: UserId,
        accessToken: AccessToken,
        artistIds: List<String>,
    ): Either<DomainError, List<SpotifyArtistDetails>> {
        if (artistIds.isEmpty()) return emptyList<SpotifyArtistDetails>().right()
        return try {
            val results = mutableListOf<SpotifyArtistDetails>()
            artistIds.chunked(BATCH_SIZE).forEach { batch ->
                val ids = batch.joinToString(",")
                val request = HttpRequest.newBuilder()
                    .uri(URI.create("$apiBaseUrl/v1/artists?ids=$ids"))
                    .header("Authorization", "Bearer ${accessToken.value}")
                    .GET()
                    .build()
                val response = httpMetrics.timed(request.uri()) {
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                }
                val errorResult = response.checkRateLimitOrError(logger, EnrichmentError.ARTIST_DETAILS_FETCH_FAILED)
                if (errorResult != null) return errorResult
                val json: JsonNode = objectMapper.readTree(response.body())
                json.get("artists")?.mapNotNullTo(results) { artist ->
                    if (artist.isNull) return@mapNotNullTo null
                    SpotifyArtistDetails(
                        artistId = artist.get("id").asText(),
                        name = artist.get("name").asText(),
                        genres = artist.get("genres")?.map { it.asText() } ?: emptyList(),
                    )
                }
            }
            results.right()
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error fetching artist details for user ${userId.value}" }
            EnrichmentError.ARTIST_DETAILS_FETCH_FAILED.left()
        }
    }

    companion object : KLogging() {
        private const val BATCH_SIZE = 50
    }
}
