package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.error.SyncError
import mu.KLogger
import java.net.http.HttpResponse
import java.time.Duration

internal const val HTTP_OK = 200
internal const val HTTP_NOT_FOUND = 404
internal const val HTTP_GONE = 410
internal const val HTTP_RATE_LIMITED = 429
internal const val DEFAULT_RETRY_AFTER_SECONDS = 60L

internal fun HttpResponse<String>.checkRateLimitOrError(
    logger: KLogger,
    fallbackError: DomainError,
): Either<DomainError, Nothing>? {
    if (statusCode() == HTTP_RATE_LIMITED) {
        val retryAfterSeconds = headers().firstValue("Retry-After")
            .map { it.toLongOrNull() ?: DEFAULT_RETRY_AFTER_SECONDS }
            .orElse(DEFAULT_RETRY_AFTER_SECONDS)
        logger.warn { "Spotify rate limit exceeded on ${request().uri().path}, retry after ${retryAfterSeconds}s" }
        return SpotifyRateLimitError(Duration.ofSeconds(retryAfterSeconds)).left()
    }
    if (statusCode() != HTTP_OK) {
        logger.error { "Spotify HTTP request failed with ${statusCode()} - ${body()}" }
        return fallbackError.left()
    }
    return null
}

/**
 * Like [checkRateLimitOrError] but additionally returns [SyncError.BULK_ENDPOINT_GONE]
 * for 404 and 410 responses so callers can detect that the bulk endpoint was removed.
 */
internal fun HttpResponse<String>.checkBulkEndpointOrError(
    logger: KLogger,
    fallbackError: DomainError,
): Either<DomainError, Nothing>? {
    if (statusCode() == HTTP_RATE_LIMITED) {
        val retryAfterSeconds = headers().firstValue("Retry-After")
            .map { it.toLongOrNull() ?: DEFAULT_RETRY_AFTER_SECONDS }
            .orElse(DEFAULT_RETRY_AFTER_SECONDS)
        logger.warn { "Spotify rate limit exceeded on ${request().uri().path}, retry after ${retryAfterSeconds}s" }
        return SpotifyRateLimitError(Duration.ofSeconds(retryAfterSeconds)).left()
    }
    if (statusCode() == HTTP_OK) return null
    val error: DomainError = if (statusCode() == HTTP_NOT_FOUND || statusCode() == HTTP_GONE) {
        logger.warn { "Bulk Spotify endpoint is gone (${statusCode()}) at ${request().uri().path}" }
        SyncError.BULK_ENDPOINT_GONE
    } else {
        logger.error { "Spotify HTTP request failed with ${statusCode()} - ${body()}" }
        fallbackError
    }
    return error.left()
}
