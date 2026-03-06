package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.SpotifyForbiddenError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import mu.KLogger
import java.net.http.HttpResponse
import java.time.Duration

internal const val HTTP_OK = 200
internal const val HTTP_FORBIDDEN = 403
internal const val HTTP_RATE_LIMITED = 429
internal const val DEFAULT_RETRY_AFTER_SECONDS = 60L

internal fun HttpResponse<String>.checkRateLimitOrError(
    logger: KLogger,
    fallbackError: DomainError,
): Either<DomainError, Nothing>? {
    if (statusCode() == HTTP_FORBIDDEN) {
        logger.warn { "Spotify access forbidden (403): ${body()}" }
        return SpotifyForbiddenError.left()
    }
    if (statusCode() == HTTP_RATE_LIMITED) {
        val retryAfterSeconds = headers().firstValue("Retry-After")
            .map { it.toLongOrNull() ?: DEFAULT_RETRY_AFTER_SECONDS }
            .orElse(DEFAULT_RETRY_AFTER_SECONDS)
        logger.warn { "Spotify rate limit exceeded, retry after ${retryAfterSeconds}s" }
        return SpotifyRateLimitError(Duration.ofSeconds(retryAfterSeconds)).left()
    }
    if (statusCode() != HTTP_OK) {
        logger.error { "Spotify HTTP request failed with ${statusCode()} - ${body()}" }
        return fallbackError.left()
    }
    return null
}
