package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.getOrElse
import de.chrgroth.spotify.control.adapter.out.spotify.model.ArtistObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.SeveralArtistsObject
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpotifyCatalogAdapterArtistsTests {

  private val apiClient = mockk<SpotifyApiRestClient>()
  private val throttler = mockk<SpotifyRequestThrottler>(relaxed = true)
  private val httpMetrics = SpotifyHttpMetrics(io.micrometer.core.instrument.simple.SimpleMeterRegistry(), mockk(relaxed = true))
  private val adapter = SpotifyCatalogAdapter(httpMetrics, throttler, apiClient)
  private val accessToken = AccessToken("token")

  @Test
  fun `getArtists resolves names for all ids in a single chunk`() {
    every { apiClient.getSeveralArtists("artist-1,artist-2") } returns SeveralArtistsObject(
      artists = listOf(
        ArtistObject(id = "artist-1", name = "Zeta"),
        ArtistObject(id = "artist-2", name = "Alpha"),
      ),
    )

    val result = adapter.getArtists(accessToken, listOf("artist-1", "artist-2")).getOrElse { emptyList() }

    assertThat(result.associate { it.id to it.artistName }).isEqualTo(
      mapOf(ArtistId("artist-1") to "Zeta", ArtistId("artist-2") to "Alpha"),
    )
  }

  @Test
  fun `getArtists keeps names resolved from earlier chunks when a later chunk fails`() {
    val firstChunkIds = (1..50).map { "artist-$it" }
    val secondChunkIds = listOf("artist-51", "artist-52")

    every { apiClient.getSeveralArtists(firstChunkIds.joinToString(",")) } returns SeveralArtistsObject(
      artists = firstChunkIds.map { ArtistObject(id = it, name = "Name $it") },
    )
    every { apiClient.getSeveralArtists(secondChunkIds.joinToString(",")) } throws SpotifyApiException(500, "boom")

    val result = adapter.getArtists(accessToken, firstChunkIds + secondChunkIds)

    val resolved = result.getOrElse { emptyList() }
    assertThat(resolved).hasSize(50)
    assertThat(resolved.map { it.id }).containsExactlyInAnyOrderElementsOf(firstChunkIds.map { ArtistId(it) })
  }

  @Test
  fun `getArtists returns error when every chunk fails`() {
    every { apiClient.getSeveralArtists("artist-1") } throws SpotifyApiException(500, "boom")

    val result = adapter.getArtists(accessToken, listOf("artist-1"))

    assertThat(result.isLeft()).isTrue()
  }
}
