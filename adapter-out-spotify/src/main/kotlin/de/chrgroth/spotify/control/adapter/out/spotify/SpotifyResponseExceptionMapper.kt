package de.chrgroth.spotify.control.adapter.out.spotify

import jakarta.ws.rs.core.MultivaluedMap
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper

internal class SpotifyResponseExceptionMapper : ResponseExceptionMapper<RuntimeException> {

  override fun handles(status: Int, headers: MultivaluedMap<String, Any>): Boolean =
    status == HTTP_NO_CONTENT || status >= HTTP_BAD_REQUEST

  override fun toThrowable(response: Response): RuntimeException = when (response.status) {
    HTTP_NO_CONTENT -> SpotifyNoContentException()
    HTTP_RATE_LIMITED -> SpotifyRateLimitException(
      response.getHeaderString("Retry-After")?.toLongOrNull() ?: DEFAULT_RETRY_AFTER_SECONDS
    )
    else -> SpotifyApiException(response.status, response.readEntity(String::class.java))
  }
}
