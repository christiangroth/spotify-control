package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.OutgoingRequestStatsPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyPlaylistTracksPort
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@QuarkusTest
class SpotifyPlaylistTracksAdapterTests {

    @Inject
    lateinit var spotifyPlaylistTracks: SpotifyPlaylistTracksPort

    @Inject
    lateinit var outgoingRequestStats: OutgoingRequestStatsPort

    @Inject
    lateinit var meterRegistry: MeterRegistry

    @Test
    fun `getPlaylistTracks returns tracks from mock`() {
        val result = spotifyPlaylistTracks.getPlaylistTracks(UserId("test-user-a"), AccessToken("mock-access-token"), "mock-playlist-1")

        assertThat(result).isInstanceOf(Either.Right::class.java)
        val playlist = (result as Either.Right).value
        assertThat(playlist.spotifyPlaylistId).isEqualTo("mock-playlist-1")
        assertThat(playlist.snapshotId).isEqualTo("mock-snapshot-1")
        assertThat(playlist.tracks).hasSize(1)
        assertThat(playlist.tracks[0].trackId).isEqualTo("track-1")
        assertThat(playlist.tracks[0].trackName).isEqualTo("Track One")
        assertThat(playlist.tracks[0].artistIds).containsExactly("artist-1")
        assertThat(playlist.tracks[0].artistNames).containsExactly("Artist One")
    }

    @Test
    fun `getPlaylistTracks filters out non-track items`() {
        val result = spotifyPlaylistTracks.getPlaylistTracks(UserId("test-user-a"), AccessToken("mock-access-token"), "mock-playlist-1")

        assertThat(result).isInstanceOf(Either.Right::class.java)
        val playlist = (result as Either.Right).value
        assertThat(playlist.tracks.none { it.trackId == "episode-1" }).isTrue
    }

    @Test
    fun `getPlaylistTracks records spotify request metrics`() {
        spotifyPlaylistTracks.getPlaylistTracks(UserId("test-user-a"), AccessToken("mock-access-token"), "mock-playlist-1")

        val timer = meterRegistry.find("spotify.request").timer()
        assertThat(timer).isNotNull
        assertThat(timer!!.count()).isGreaterThan(0)
    }

    @Test
    fun `getPlaylistTracks increments in-memory request counter`() {
        spotifyPlaylistTracks.getPlaylistTracks(UserId("test-user-a"), AccessToken("mock-access-token"), "mock-playlist-1")

        val stats = outgoingRequestStats.getRequestStats()
        assertThat(stats).isNotEmpty
        assertThat(stats.any { it.requestCountLast24h > 0 }).isTrue
    }
}
