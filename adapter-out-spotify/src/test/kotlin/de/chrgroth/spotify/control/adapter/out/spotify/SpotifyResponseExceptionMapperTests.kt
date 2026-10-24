package de.chrgroth.spotify.control.adapter.out.spotify

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpotifyResponseExceptionMapperTests {

  @Test
  fun `uses the Retry-After header of a rate limited response`() {
    assertThat(retryAfterSeconds("24798")).isEqualTo(24798L)
  }

  @Test
  fun `enforces a minimum retry delay when Spotify answers Retry-After 0`() {
    assertThat(retryAfterSeconds("0")).isEqualTo(MIN_RETRY_AFTER_SECONDS)
  }

  @Test
  fun `falls back to the default retry delay when the header is missing or invalid`() {
    assertThat(retryAfterSeconds(null)).isEqualTo(DEFAULT_RETRY_AFTER_SECONDS)
    assertThat(retryAfterSeconds("soon")).isEqualTo(DEFAULT_RETRY_AFTER_SECONDS)
  }
}
