package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.Playlist
import de.chrgroth.spotify.control.domain.model.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.PlaylistDataRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class PlaylistDataRepositoryTests {

    @Inject
    lateinit var playlistDataRepository: PlaylistDataRepositoryPort

    private fun buildPlaylist(playlistId: String, snapshotId: String = "snap-1") = Playlist(
        spotifyPlaylistId = playlistId,
        snapshotId = snapshotId,
        tracks = listOf(
            PlaylistTrack(
                trackId = "track-1",
                trackName = "Track One",
                artistIds = listOf("artist-1"),
                artistNames = listOf("Artist One"),
            ),
        ),
    )

    @Test
    fun `findByUserIdAndPlaylistId returns null when no playlist exists`() {
        val userId = UserId("no-playlist-${UUID.randomUUID()}")

        assertThat(playlistDataRepository.findByUserIdAndPlaylistId(userId, "unknown-playlist")).isNull()
    }

    @Test
    fun `save and findByUserIdAndPlaylistId round-trips playlist correctly`() {
        val userId = UserId("test-${UUID.randomUUID()}")
        val playlist = buildPlaylist("playlist-1")

        playlistDataRepository.save(userId, playlist)

        val found = playlistDataRepository.findByUserIdAndPlaylistId(userId, "playlist-1")
        assertThat(found).isNotNull
        assertThat(found!!.spotifyPlaylistId).isEqualTo("playlist-1")
        assertThat(found.snapshotId).isEqualTo("snap-1")
        assertThat(found.tracks).hasSize(1)
        assertThat(found.tracks[0].trackId).isEqualTo("track-1")
        assertThat(found.tracks[0].trackName).isEqualTo("Track One")
        assertThat(found.tracks[0].artistIds).containsExactly("artist-1")
        assertThat(found.tracks[0].artistNames).containsExactly("Artist One")
    }

    @Test
    fun `save overwrites previous playlist data`() {
        val userId = UserId("test-${UUID.randomUUID()}")
        playlistDataRepository.save(userId, buildPlaylist("playlist-1", "snap-1"))

        playlistDataRepository.save(userId, buildPlaylist("playlist-1", "snap-2"))

        val found = playlistDataRepository.findByUserIdAndPlaylistId(userId, "playlist-1")
        assertThat(found).isNotNull
        assertThat(found!!.snapshotId).isEqualTo("snap-2")
    }

    @Test
    fun `save does not affect playlists of other users`() {
        val userId1 = UserId("test-${UUID.randomUUID()}")
        val userId2 = UserId("test-${UUID.randomUUID()}")
        playlistDataRepository.save(userId1, buildPlaylist("playlist-1"))
        playlistDataRepository.save(userId2, buildPlaylist("playlist-2"))

        assertThat(playlistDataRepository.findByUserIdAndPlaylistId(userId1, "playlist-1")).isNotNull
        assertThat(playlistDataRepository.findByUserIdAndPlaylistId(userId2, "playlist-2")).isNotNull
        assertThat(playlistDataRepository.findByUserIdAndPlaylistId(userId1, "playlist-2")).isNull()
        assertThat(playlistDataRepository.findByUserIdAndPlaylistId(userId2, "playlist-1")).isNull()
    }
}
