package de.chrgroth.spotify.control.domain.check

import de.chrgroth.spotify.control.domain.model.Playlist
import de.chrgroth.spotify.control.domain.model.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.PlaylistType
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Clock

class YearSongsInAllCheckRunnerTests {

    private val playlistRepository: PlaylistRepositoryPort = mockk()
    private val runner = YearSongsInAllCheckRunner(playlistRepository)

    private val userId = UserId("user-1")
    private val playlistId = "playlist-1"
    private val allPlaylistId = "playlist-all"

    private fun buildTrack(trackId: String, artistName: String = "Artist", trackName: String = "Track $trackId") = PlaylistTrack(
        trackId = trackId,
        trackName = trackName,
        artistIds = listOf("artist-1"),
        artistNames = listOf(artistName),
        albumId = "album-1",
    )

    private fun buildPlaylist(spotifyPlaylistId: String = playlistId, tracks: List<PlaylistTrack>) = Playlist(
        spotifyPlaylistId = spotifyPlaylistId,
        snapshotId = "snap-1",
        tracks = tracks,
    )

    private fun buildPlaylistInfo(spotifyPlaylistId: String = playlistId, type: PlaylistType? = null) = PlaylistInfo(
        spotifyPlaylistId = spotifyPlaylistId,
        snapshotId = "snap-1",
        lastSnapshotIdSyncTime = Clock.System.now(),
        name = "Playlist $spotifyPlaylistId",
        syncStatus = PlaylistSyncStatus.ACTIVE,
        type = type,
    )

    @Test
    fun `isApplicable returns true only for YEAR playlists`() {
        assertThat(runner.isApplicable(null)).isFalse()
        assertThat(runner.isApplicable(buildPlaylistInfo(type = PlaylistType.YEAR))).isTrue()
        assertThat(runner.isApplicable(buildPlaylistInfo(type = PlaylistType.ALL))).isFalse()
        assertThat(runner.isApplicable(buildPlaylistInfo(type = PlaylistType.UNKNOWN))).isFalse()
    }

    @Test
    fun `run passes when no all playlist exists`() {
        val playlist = buildPlaylist(tracks = listOf(buildTrack("t1")))
        val currentPlaylistInfo = buildPlaylistInfo(type = PlaylistType.YEAR)
        val playlistInfos = listOf(currentPlaylistInfo)

        val result = runner.run(userId, playlistId, playlist, currentPlaylistInfo, playlistInfos)

        assertThat(result.succeeded).isTrue()
        assertThat(result.violations).isEmpty()
        assertThat(result.checkId).isEqualTo("$playlistId:year-songs-in-all")
    }

    @Test
    fun `run passes when all playlist is not yet synced`() {
        val playlist = buildPlaylist(tracks = listOf(buildTrack("t1")))
        val currentPlaylistInfo = buildPlaylistInfo(type = PlaylistType.YEAR)
        val playlistInfos = listOf(
            currentPlaylistInfo,
            buildPlaylistInfo(spotifyPlaylistId = allPlaylistId, type = PlaylistType.ALL),
        )
        every { playlistRepository.findByUserIdAndPlaylistId(userId, allPlaylistId) } returns null

        val result = runner.run(userId, playlistId, playlist, currentPlaylistInfo, playlistInfos)

        assertThat(result.succeeded).isTrue()
        assertThat(result.violations).isEmpty()
    }

    @Test
    fun `run passes when all tracks are in all playlist`() {
        val playlist = buildPlaylist(tracks = listOf(buildTrack("t1"), buildTrack("t2")))
        val allPlaylist = buildPlaylist(spotifyPlaylistId = allPlaylistId, tracks = listOf(buildTrack("t1"), buildTrack("t2"), buildTrack("t3")))
        val currentPlaylistInfo = buildPlaylistInfo(type = PlaylistType.YEAR)
        val playlistInfos = listOf(
            currentPlaylistInfo,
            buildPlaylistInfo(spotifyPlaylistId = allPlaylistId, type = PlaylistType.ALL),
        )
        every { playlistRepository.findByUserIdAndPlaylistId(userId, allPlaylistId) } returns allPlaylist

        val result = runner.run(userId, playlistId, playlist, currentPlaylistInfo, playlistInfos)

        assertThat(result.succeeded).isTrue()
        assertThat(result.violations).isEmpty()
    }

    @Test
    fun `run reports violations for tracks missing from all playlist`() {
        val playlist = buildPlaylist(
            tracks = listOf(
                buildTrack("t1", artistName = "Artist A", trackName = "Song A"),
                buildTrack("t2", artistName = "Artist B", trackName = "Song B"),
                buildTrack("t3", artistName = "Artist C", trackName = "Song C"),
            ),
        )
        val allPlaylist = buildPlaylist(spotifyPlaylistId = allPlaylistId, tracks = listOf(buildTrack("t1")))
        val currentPlaylistInfo = buildPlaylistInfo(type = PlaylistType.YEAR)
        val playlistInfos = listOf(
            currentPlaylistInfo,
            buildPlaylistInfo(spotifyPlaylistId = allPlaylistId, type = PlaylistType.ALL),
        )
        every { playlistRepository.findByUserIdAndPlaylistId(userId, allPlaylistId) } returns allPlaylist

        val result = runner.run(userId, playlistId, playlist, currentPlaylistInfo, playlistInfos)

        assertThat(result.succeeded).isFalse()
        assertThat(result.violations).containsExactlyInAnyOrder("Artist B – Song B", "Artist C – Song C")
    }

    @Test
    fun `run deduplicates violations for duplicate missing tracks`() {
        val playlist = buildPlaylist(
            tracks = listOf(
                buildTrack("t1", artistName = "Artist A", trackName = "Song A"),
                buildTrack("t2", artistName = "Artist B", trackName = "Song B"),
                buildTrack("t2", artistName = "Artist B", trackName = "Song B"),
            ),
        )
        val allPlaylist = buildPlaylist(spotifyPlaylistId = allPlaylistId, tracks = listOf(buildTrack("t1")))
        val currentPlaylistInfo = buildPlaylistInfo(type = PlaylistType.YEAR)
        val playlistInfos = listOf(
            currentPlaylistInfo,
            buildPlaylistInfo(spotifyPlaylistId = allPlaylistId, type = PlaylistType.ALL),
        )
        every { playlistRepository.findByUserIdAndPlaylistId(userId, allPlaylistId) } returns allPlaylist

        val result = runner.run(userId, playlistId, playlist, currentPlaylistInfo, playlistInfos)

        assertThat(result.succeeded).isFalse()
        assertThat(result.violations).containsExactly("Artist B – Song B")
    }
}
