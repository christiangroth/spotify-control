package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import jakarta.ws.rs.core.Response
import mu.KLogger
import kotlin.time.Duration.Companion.seconds

internal const val HTTP_OK = 200
internal const val HTTP_NO_CONTENT = 204
internal const val HTTP_CREATED = 201
internal const val HTTP_RATE_LIMITED = 429
internal const val DEFAULT_RETRY_AFTER_SECONDS = 60L

internal fun Response.checkRateLimitOrError(
  logger: KLogger,
  urlTemplate: String,
  fallbackError: DomainError,
  vararg additionalSuccessCodes: Int,
): Either<DomainError, Nothing>? {
  val statusCode = status
  if (statusCode == HTTP_RATE_LIMITED) {
    val retryAfterSeconds = getHeaderString("Retry-After")?.toLongOrNull() ?: DEFAULT_RETRY_AFTER_SECONDS
    logger.warn { "Spotify rate limit exceeded on $urlTemplate, retry after ${retryAfterSeconds}s" }
    return SpotifyRateLimitError(retryAfterSeconds.seconds).left()
  }
  if (statusCode != HTTP_OK && statusCode !in additionalSuccessCodes) {
    logger.error { "Spotify HTTP request to $urlTemplate failed with $statusCode - ${readEntity(String::class.java)}" }
    return fallbackError.left()
  }
  return null
}

internal fun String.queryParamInt(name: String): Int? =
  substringAfter('?', "").split('&')
    .firstOrNull { it.startsWith("$name=") }
    ?.substringAfter('=')
    ?.toIntOrNull()

internal fun String.queryParamLong(name: String): Long? =
  substringAfter('?', "").split('&')
    .firstOrNull { it.startsWith("$name=") }
    ?.substringAfter('=')
    ?.toLongOrNull()

