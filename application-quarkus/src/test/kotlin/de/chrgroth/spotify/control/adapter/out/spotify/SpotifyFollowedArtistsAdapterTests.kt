package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.port.out.catalog.SpotifyCatalogPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@QuarkusTest
class SpotifyFollowedArtistsAdapterTests {

  @Inject
  lateinit var spotifyCatalog: SpotifyCatalogPort

  @Test
  fun `getFollowedArtists returns artists from mock`() {
    val result = spotifyCatalog.getFollowedArtists(AccessToken("mock-access-token"))

    assertThat(result).isInstanceOf(Either.Right::class.java)
    val artists = (result as Either.Right).value
    assertThat(artists.map { it.id }).containsExactlyInAnyOrder(ArtistId("artist-1"), ArtistId("artist-2"))
    assertThat(artists.map { it.artistName }).containsExactlyInAnyOrder("Artist One", "Artist Two")
  }
}
