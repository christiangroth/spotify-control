package de.chrgroth.spotify.control.domain.check

import de.chrgroth.spotify.control.domain.model.AlbumId
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.ArtistId
import de.chrgroth.spotify.control.domain.model.Playlist
import de.chrgroth.spotify.control.domain.model.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.PlaylistType
import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

class DuplicateTrackIdsCheckRunnerTests {

    private val appTrackRepository: AppTrackRepositoryPort = mockk()
    private val runner = DuplicateTrackIdsCheckRunner(appTrackRepository)

    private val userId = UserId("user-1")
    private val playlistId = "playlist-1"

    private fun buildTrack(trackId: String) = PlaylistTrack(
        trackId = TrackId(trackId),
        artistIds = listOf(ArtistId("artist-1")),
        albumId = AlbumId("album-1"),
    )

    private fun buildAppTrack(trackId: String, title: String, artistName: String? = "Artist") = AppTrack(
        id = TrackId(trackId),
        title = title,
        artistId = ArtistId("artist-1"),
        artistName = artistName,
        lastSync = Instant.fromEpochMilliseconds(0),
    )

    private fun buildPlaylist(tracks: List<PlaylistTrack>) = Playlist(
        spotifyPlaylistId = playlistId,
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

        val result = runner.run(userId, playlistId, playlist, null, emptyList())

        assertThat(result.succeeded).isTrue()
        assertThat(result.violations).isEmpty()
        assertThat(result.checkId).isEqualTo("$playlistId:duplicate-track-ids")
    }

    @Test
    fun `run detects duplicate track ids`() {
        val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t1"), buildTrack("t2")))
        every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"))) } returns listOf(buildAppTrack("t1", "Song A", "Artist A"))

        val result = runner.run(userId, playlistId, playlist, null, emptyList())

        assertThat(result.succeeded).isFalse()
        assertThat(result.violations).containsExactly("Artist A – Song A")
    }

    @Test
    fun `run reports all duplicate tracks`() {
        val playlist = buildPlaylist(
            listOf(
                buildTrack("t1"),
                buildTrack("t1"),
                buildTrack("t2"),
                buildTrack("t2"),
            ),
        )
        every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"), TrackId("t2"))) } returns listOf(
            buildAppTrack("t1", "Song A", "Artist A"),
            buildAppTrack("t2", "Song B", "Artist B"),
        )

        val result = runner.run(userId, playlistId, playlist, null, emptyList())

        assertThat(result.succeeded).isFalse()
        assertThat(result.violations).containsExactlyInAnyOrder("Artist A – Song A", "Artist B – Song B")
    }

    @Test
    fun `run falls back to unknown artist when artistName is null`() {
        val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t1")))
        every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"))) } returns listOf(buildAppTrack("t1", "Song", null))

        val result = runner.run(userId, playlistId, playlist, null, emptyList())

        assertThat(result.violations).containsExactly("Unknown Artist – Song")
    }
}
