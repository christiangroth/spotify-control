package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.SpotifyRecentlyPlayedPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@QuarkusTest
class SpotifyRecentlyPlayedAdapterTests {

    @Inject
    lateinit var spotifyRecentlyPlayed: SpotifyRecentlyPlayedPort

    @Test
    fun `getRecentlyPlayed returns items from mock`() {
        val result = spotifyRecentlyPlayed.getRecentlyPlayed(UserId("test-user-a"), AccessToken("mock-access-token"))

        assertThat(result).isInstanceOf(Either.Right::class.java)
        val items = (result as Either.Right).value
        assertThat(items).hasSize(1)
        assertThat(items[0].trackId).isEqualTo("track-1")
        assertThat(items[0].trackName).isEqualTo("Track One")
        assertThat(items[0].artistIds).containsExactly("artist-1")
        assertThat(items[0].artistNames).containsExactly("Artist One")
        assertThat(items[0].spotifyUserId).isEqualTo(UserId("test-user-a"))
    }
}
