package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.SpotifyRecentlyPlayedPort
import de.chrgroth.spotify.control.domain.port.out.OutgoingRequestStatsPort
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@QuarkusTest
class SpotifyRecentlyPlayedAdapterTests {

    @Inject
    lateinit var spotifyRecentlyPlayed: SpotifyRecentlyPlayedPort

    @Inject
    lateinit var outgoingRequestStats: OutgoingRequestStatsPort

    @Inject
    lateinit var meterRegistry: MeterRegistry

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

    @Test
    fun `getRecentlyPlayed filters out podcast episodes`() {
        val result = spotifyRecentlyPlayed.getRecentlyPlayed(UserId("test-user-a"), AccessToken("mock-access-token"))

        assertThat(result).isInstanceOf(Either.Right::class.java)
        val items = (result as Either.Right).value
        assertThat(items.none { it.trackId == "episode-1" }).isTrue
    }

    @Test
    fun `getRecentlyPlayed records spotify request metrics`() {
        spotifyRecentlyPlayed.getRecentlyPlayed(UserId("test-user-a"), AccessToken("mock-access-token"))

        val timer = meterRegistry.find("spotify.request").timer()
        assertThat(timer).isNotNull
        assertThat(timer!!.count()).isGreaterThan(0)
    }

    @Test
    fun `getRecentlyPlayed increments in-memory request counter`() {
        spotifyRecentlyPlayed.getRecentlyPlayed(UserId("test-user-a"), AccessToken("mock-access-token"))

        val stats = outgoingRequestStats.getRequestStats()
        assertThat(stats).isNotEmpty
        assertThat(stats.any { it.requestCountLast24h > 0 }).isTrue
    }
}
