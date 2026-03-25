package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.Playlist
import de.chrgroth.spotify.control.domain.model.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class PlaylistDataRepositoryTests {

    @Inject
    lateinit var playlistRepository: PlaylistRepositoryPort

    private fun buildPlaylist(playlistId: String) = Playlist(
        spotifyPlaylistId = playlistId,
        tracks = listOf(
            PlaylistTrack(
                trackId = "track-1",
                artistIds = listOf("artist-1"),
                albumId = "album-1",
            ),
        ),
    )

    private fun buildPlaylistInfo(playlistId: String, syncStatus: PlaylistSyncStatus = PlaylistSyncStatus.PASSIVE): PlaylistInfo {
        val now = Clock.System.now().let { Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) }
        return PlaylistInfo(
            spotifyPlaylistId = playlistId,
            snapshotId = "snap-1",
            lastSnapshotIdSyncTime = now - 1.hours,
            name = "Playlist $playlistId",
            syncStatus = syncStatus,
        )
    }

    @Test
    fun `findByUserIdAndPlaylistId returns null when no playlist exists`() {
        val userId = UserId("no-playlist-${UUID.randomUUID()}")

        assertThat(playlistRepository.findByUserIdAndPlaylistId(userId, "unknown-playlist")).isNull()
    }

    @Test
    fun `save and findByUserIdAndPlaylistId round-trips playlist correctly`() {
        val userId = UserId("test-${UUID.randomUUID()}")
        val playlist = buildPlaylist("playlist-1")

        playlistRepository.save(userId, playlist)

        val found = playlistRepository.findByUserIdAndPlaylistId(userId, "playlist-1")
        assertThat(found).isNotNull
        assertThat(found!!.spotifyPlaylistId).isEqualTo("playlist-1")
        assertThat(found.tracks).hasSize(1)
        assertThat(found.tracks[0].trackId).isEqualTo("track-1")
        assertThat(found.tracks[0].artistIds).containsExactly("artist-1")
    }

    @Test
    fun `numberOfTracks returns correct size after save`() {
        val userId = UserId("test-${UUID.randomUUID()}")
        val playlistId = "playlist-ntracks-${UUID.randomUUID()}"
        val playlist = Playlist(
            spotifyPlaylistId = playlistId,
            tracks = listOf(
                PlaylistTrack(trackId = "t1", artistIds = listOf("a1"), albumId = "al1"),
                PlaylistTrack(trackId = "t2", artistIds = listOf("a2"), albumId = "al2"),
            ),
        )

        playlistRepository.save(userId, playlist)

        val found = playlistRepository.findByUserIdAndPlaylistId(userId, playlistId)
        assertThat(found).isNotNull
        assertThat(found!!.numberOfTracks).isEqualTo(2)
    }

    @Test
    fun `numberOfTracks returns correct size after appendTracks`() {
        val userId = UserId("test-${UUID.randomUUID()}")
        val playlistId = "playlist-append-${UUID.randomUUID()}"
        playlistRepository.save(
            userId,
            Playlist(
                spotifyPlaylistId = playlistId,
                tracks = listOf(PlaylistTrack(trackId = "t1", artistIds = listOf("a1"), albumId = "al1")),
            ),
        )

        playlistRepository.appendTracks(
            userId,
            playlistId,
            listOf(
                PlaylistTrack(trackId = "t2", artistIds = listOf("a2"), albumId = "al2"),
                PlaylistTrack(trackId = "t3", artistIds = listOf("a3"), albumId = "al3"),
            ),
        )

        val found = playlistRepository.findByUserIdAndPlaylistId(userId, playlistId)
        assertThat(found).isNotNull
        assertThat(found!!.numberOfTracks).isEqualTo(3)
    }

    @Test
    fun `save overwrites previous playlist data`() {
        val userId = UserId("test-${UUID.randomUUID()}")
        playlistRepository.save(userId, buildPlaylist("playlist-1"))

        playlistRepository.save(userId, buildPlaylist("playlist-1"))

        val found = playlistRepository.findByUserIdAndPlaylistId(userId, "playlist-1")
        assertThat(found).isNotNull
        assertThat(found!!.tracks).hasSize(1)
    }

    @Test
    fun `save does not affect playlists of other users`() {
        val userId1 = UserId("test-${UUID.randomUUID()}")
        val userId2 = UserId("test-${UUID.randomUUID()}")
        playlistRepository.save(userId1, buildPlaylist("playlist-1"))
        playlistRepository.save(userId2, buildPlaylist("playlist-2"))

        assertThat(playlistRepository.findByUserIdAndPlaylistId(userId1, "playlist-1")).isNotNull
        assertThat(playlistRepository.findByUserIdAndPlaylistId(userId2, "playlist-2")).isNotNull
        assertThat(playlistRepository.findByUserIdAndPlaylistId(userId1, "playlist-2")).isNull()
        assertThat(playlistRepository.findByUserIdAndPlaylistId(userId2, "playlist-1")).isNull()
    }

    @Test
    fun `findArtistIdsInActivePlaylists returns artist ids from active playlists`() {
        val userId = UserId("test-${UUID.randomUUID()}")
        val playlistId = "playlist-active-${UUID.randomUUID()}"
        val artistId = "artist-active-${UUID.randomUUID()}"

        playlistRepository.saveAll(userId, listOf(buildPlaylistInfo(playlistId, PlaylistSyncStatus.ACTIVE)))
        playlistRepository.save(
            userId,
            Playlist(
                spotifyPlaylistId = playlistId,
                tracks = listOf(
                    PlaylistTrack(
                        trackId = "track-x",
                        artistIds = listOf(artistId),
                        albumId = "album-x",
                    ),
                ),
            ),
        )

        val result = playlistRepository.findArtistIdsInActivePlaylists()

        assertThat(result).contains(artistId)
    }

    @Test
    fun `findArtistIdsInActivePlaylists does not include artists from passive playlists`() {
        val userId = UserId("test-${UUID.randomUUID()}")
        val playlistId = "playlist-passive-${UUID.randomUUID()}"
        val artistId = "artist-passive-${UUID.randomUUID()}"

        playlistRepository.saveAll(userId, listOf(buildPlaylistInfo(playlistId, PlaylistSyncStatus.PASSIVE)))
        playlistRepository.save(
            userId,
            Playlist(
                spotifyPlaylistId = playlistId,
                tracks = listOf(
                    PlaylistTrack(
                        trackId = "track-y",
                        artistIds = listOf(artistId),
                        albumId = "album-y",
                    ),
                ),
            ),
        )

        val result = playlistRepository.findArtistIdsInActivePlaylists()

        assertThat(result).doesNotContain(artistId)
    }

    @Test
    fun `findArtistIdsInActivePlaylists collects artists from multiple active playlists across users`() {
        val userId1 = UserId("test-${UUID.randomUUID()}")
        val userId2 = UserId("test-${UUID.randomUUID()}")
        val playlistId1 = "playlist-multi-1-${UUID.randomUUID()}"
        val playlistId2 = "playlist-multi-2-${UUID.randomUUID()}"
        val artistId1 = "artist-multi-1-${UUID.randomUUID()}"
        val artistId2 = "artist-multi-2-${UUID.randomUUID()}"

        playlistRepository.saveAll(userId1, listOf(buildPlaylistInfo(playlistId1, PlaylistSyncStatus.ACTIVE)))
        playlistRepository.save(
            userId1,
            Playlist(
                spotifyPlaylistId = playlistId1,
                tracks = listOf(PlaylistTrack("t1", listOf(artistId1), "album-t1")),
            ),
        )
        playlistRepository.saveAll(userId2, listOf(buildPlaylistInfo(playlistId2, PlaylistSyncStatus.ACTIVE)))
        playlistRepository.save(
            userId2,
            Playlist(
                spotifyPlaylistId = playlistId2,
                tracks = listOf(PlaylistTrack("t2", listOf(artistId2), "album-t2")),
            ),
        )

        val result = playlistRepository.findArtistIdsInActivePlaylists()

        assertThat(result).contains(artistId1, artistId2)
    }
}
