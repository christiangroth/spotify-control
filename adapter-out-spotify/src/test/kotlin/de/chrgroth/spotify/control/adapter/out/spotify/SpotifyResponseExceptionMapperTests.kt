package de.chrgroth.spotify.control.adapter.out.spotify

import io.mockk.every
import io.mockk.mockk
import jakarta.ws.rs.core.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpotifyResponseExceptionMapperTests {

  private val mapper = SpotifyResponseExceptionMapper()

  private fun rateLimited(retryAfter: String?): Response = mockk {
    every { status } returns HTTP_RATE_LIMITED
    every { getHeaderString("Retry-After") } returns retryAfter
  }

  @Test
  fun `uses the Retry-After header of a rate limited response`() {
    val exception = mapper.toThrowable(rateLimited("24798"))

    assertThat(exception).isInstanceOf(SpotifyRateLimitException::class.java)
    assertThat((exception as SpotifyRateLimitException).retryAfterSeconds).isEqualTo(24798L)
  }

  @Test
  fun `enforces a minimum retry delay when Spotify answers Retry-After 0`() {
    val exception = mapper.toThrowable(rateLimited("0")) as SpotifyRateLimitException

    assertThat(exception.retryAfterSeconds).isEqualTo(MIN_RETRY_AFTER_SECONDS)
  }

  @Test
  fun `falls back to the default retry delay when the header is missing`() {
    val exception = mapper.toThrowable(rateLimited(null)) as SpotifyRateLimitException

    assertThat(exception.retryAfterSeconds).isEqualTo(DEFAULT_RETRY_AFTER_SECONDS)
  }
}
