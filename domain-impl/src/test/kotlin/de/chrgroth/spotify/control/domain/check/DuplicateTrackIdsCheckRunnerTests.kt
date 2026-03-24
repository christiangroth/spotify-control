package de.chrgroth.spotify.control.domain.check

import de.chrgroth.spotify.control.domain.model.Playlist
import de.chrgroth.spotify.control.domain.model.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.PlaylistType
import de.chrgroth.spotify.control.domain.model.UserId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Clock

class DuplicateTrackIdsCheckRunnerTests {

    private val runner = DuplicateTrackIdsCheckRunner()

    private val userId = UserId("user-1")
    private val playlistId = "playlist-1"

    private fun buildTrack(trackId: String, artistName: String = "Artist", trackName: String = "Track $trackId") = PlaylistTrack(
        trackId = trackId,
        trackName = trackName,
        artistIds = listOf("artist-1"),
        artistNames = listOf(artistName),
        albumId = "album-1",
    )

    private fun buildPlaylist(tracks: List<PlaylistTrack>) = Playlist(
        spotifyPlaylistId = playlistId,
        snapshotId = "snap-1",
        tracks = tracks,
    )

    private fun buildPlaylistInfo(type: PlaylistType? = null) = PlaylistInfo(
        spotifyPlaylistId = playlistId,
        snapshotId = "snap-1",
        lastSnapshotIdSyncTime = Clock.System.now(),
        name = "Playlist $playlistId",
        syncStatus = PlaylistSyncStatus.ACTIVE,
        type = type,
    )

    @Test
    fun `isApplicable returns true for any playlist type`() {
        assertThat(runner.isApplicable(null)).isTrue()
        assertThat(runner.isApplicable(buildPlaylistInfo(PlaylistType.ALL))).isTrue()
        assertThat(runner.isApplicable(buildPlaylistInfo(PlaylistType.YEAR))).isTrue()
        assertThat(runner.isApplicable(buildPlaylistInfo(PlaylistType.UNKNOWN))).isTrue()
    }

    @Test
    fun `run returns no violations for unique tracks`() {
        val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t2")))

        val result = runner.run(userId, playlistId, playlist, emptyList())

        assertThat(result.succeeded).isTrue()
        assertThat(result.violations).isEmpty()
        assertThat(result.checkId).isEqualTo("$playlistId:duplicate-track-ids")
    }

    @Test
    fun `run detects duplicate track ids`() {
        val playlist = buildPlaylist(
            listOf(
                buildTrack("t1", artistName = "Artist A", trackName = "Song A"),
                buildTrack("t1", artistName = "Artist A", trackName = "Song A"),
                buildTrack("t2"),
            ),
        )

        val result = runner.run(userId, playlistId, playlist, emptyList())

        assertThat(result.succeeded).isFalse()
        assertThat(result.violations).containsExactly("Artist A – Song A")
    }

    @Test
    fun `run reports all duplicate tracks`() {
        val playlist = buildPlaylist(
            listOf(
                buildTrack("t1", artistName = "Artist A", trackName = "Song A"),
                buildTrack("t1", artistName = "Artist A", trackName = "Song A"),
                buildTrack("t2", artistName = "Artist B", trackName = "Song B"),
                buildTrack("t2", artistName = "Artist B", trackName = "Song B"),
            ),
        )

        val result = runner.run(userId, playlistId, playlist, emptyList())

        assertThat(result.succeeded).isFalse()
        assertThat(result.violations).containsExactlyInAnyOrder("Artist A – Song A", "Artist B – Song B")
    }

    @Test
    fun `run uses first artist name or falls back to artist id then unknown artist`() {
        val noNamesTrack = PlaylistTrack(
            trackId = "t1",
            trackName = "Song",
            artistIds = listOf("artist-id-1"),
            artistNames = emptyList(),
            albumId = "album-1",
        )
        val noArtistInfoTrack = PlaylistTrack(
            trackId = "t2",
            trackName = "Song2",
            artistIds = emptyList(),
            artistNames = emptyList(),
            albumId = "album-1",
        )
        val playlist = buildPlaylist(listOf(noNamesTrack, noNamesTrack, noArtistInfoTrack, noArtistInfoTrack))

        val result = runner.run(userId, playlistId, playlist, emptyList())

        assertThat(result.violations).containsExactlyInAnyOrder("artist-id-1 – Song", "Unknown Artist – Song2")
    }
}
