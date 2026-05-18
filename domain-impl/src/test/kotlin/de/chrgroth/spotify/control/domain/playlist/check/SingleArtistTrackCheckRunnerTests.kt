package de.chrgroth.spotify.control.domain.playlist.check

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Clock
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SingleArtistTrackCheckRunnerTests {

  private val appTrackRepository: AppTrackRepositoryPort = mockk()
  private val runner = SingleArtistTrackCheckRunner(appTrackRepository)

  private val userId = UserId("user-1")
  private val playlistId = "playlist-1"

  private fun buildPlaylistTrack(trackId: String, vararg artistIds: String, albumId: String? = null) = PlaylistTrack(
    trackId = TrackId(trackId),
    artistIds = artistIds.map { ArtistId(it) },
    albumId = albumId?.let { AlbumId(it) },
  )

  private fun buildAppTrack(trackId: String, title: String, artistId: String, artistName: String? = null) = AppTrack(
    id = TrackId(trackId),
    title = title,
    artistId = ArtistId(artistId),
    artistName = artistName,
    lastSync = Instant.fromEpochMilliseconds(0),
  )

  private fun buildPlaylist(vararg tracks: PlaylistTrack) = Playlist(
    spotifyPlaylistId = playlistId,
    tracks = tracks.toList(),
  )

  private fun buildPlaylistInfo(type: PlaylistType?) = PlaylistInfo(
    spotifyPlaylistId = playlistId,
    snapshotId = "snap-1",
    lastSnapshotIdSyncTime = Clock.System.now(),
    name = "Playlist $playlistId",
    syncStatus = PlaylistSyncStatus.ACTIVE,
    type = type,
  )

  @Test
  fun `isApplicable returns true only for SINGULARITY playlists`() {
    assertThat(runner.isApplicable(null)).isFalse()
    assertThat(runner.isApplicable(buildPlaylistInfo(PlaylistType.SINGULARITY))).isTrue()
    assertThat(runner.isApplicable(buildPlaylistInfo(PlaylistType.ALL))).isFalse()
    assertThat(runner.isApplicable(buildPlaylistInfo(PlaylistType.YEAR))).isFalse()
    assertThat(runner.isApplicable(buildPlaylistInfo(PlaylistType.UNKNOWN))).isFalse()
  }

  @Test
  fun `run returns no violations for empty playlist`() {
    val result = runner.run(userId, playlistId, buildPlaylist(), null, emptyList())

    assertThat(result.succeeded).isTrue()
    assertThat(result.violations).isEmpty()
  }

  @Test
  fun `run returns no violations when each artist has exactly one track`() {
    val t1 = buildPlaylistTrack("t1", "artist-1")
    val t2 = buildPlaylistTrack("t2", "artist-2")
    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"), TrackId("t2"))) } returns listOf(
      buildAppTrack("t1", "Song A", "artist-1", "Artist One"),
      buildAppTrack("t2", "Song B", "artist-2", "Artist Two"),
    )

    val result = runner.run(userId, playlistId, buildPlaylist(t1, t2), null, emptyList())

    assertThat(result.succeeded).isTrue()
    assertThat(result.violations).isEmpty()
    assertThat(result.checkId).isEqualTo("$playlistId:single-artist-track")
  }

  @Test
  fun `run reports violation when artist appears in two tracks`() {
    val t1 = buildPlaylistTrack("t1", "artist-1")
    val t2 = buildPlaylistTrack("t2", "artist-1")
    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"), TrackId("t2"))) } returns listOf(
      buildAppTrack("t1", "Song A", "artist-1", "Artist One"),
      buildAppTrack("t2", "Song B", "artist-1", "Artist One"),
    )

    val result = runner.run(userId, playlistId, buildPlaylist(t1, t2), null, emptyList())

    assertThat(result.succeeded).isFalse()
    assertThat(result.violations).containsExactlyInAnyOrder(
      "Artist One – Song A",
      "Artist One – Song B",
    )
  }

  @Test
  fun `run reports only the artist with multiple tracks`() {
    val t1 = buildPlaylistTrack("t1", "artist-1")
    val t2 = buildPlaylistTrack("t2", "artist-1")
    val t3 = buildPlaylistTrack("t3", "artist-2")
    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"), TrackId("t2"), TrackId("t3"))) } returns listOf(
      buildAppTrack("t1", "Song A", "artist-1", "Artist One"),
      buildAppTrack("t2", "Song B", "artist-1", "Artist One"),
      buildAppTrack("t3", "Song C", "artist-2", "Artist Two"),
    )

    val result = runner.run(userId, playlistId, buildPlaylist(t1, t2, t3), null, emptyList())

    assertThat(result.succeeded).isFalse()
    assertThat(result.violations).containsExactlyInAnyOrder("Artist One – Song A", "Artist One – Song B")
  }

  @Test
  fun `run violations are sorted alphabetically`() {
    val t1 = buildPlaylistTrack("t1", "artist-1")
    val t2 = buildPlaylistTrack("t2", "artist-1")
    val t3 = buildPlaylistTrack("t3", "artist-1")
    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"), TrackId("t2"), TrackId("t3"))) } returns listOf(
      buildAppTrack("t1", "Zebra Song", "artist-1", "Artist One"),
      buildAppTrack("t2", "Alpha Song", "artist-1", "Artist One"),
      buildAppTrack("t3", "Middle Song", "artist-1", "Artist One"),
    )

    val result = runner.run(userId, playlistId, buildPlaylist(t1, t2, t3), null, emptyList())

    assertThat(result.succeeded).isFalse()
    assertThat(result.violations).containsExactly(
      "Artist One – Alpha Song",
      "Artist One – Middle Song",
      "Artist One – Zebra Song",
    )
  }

  @Test
  fun `run falls back to artistId when appTrack has no artistName`() {
    val t1 = buildPlaylistTrack("t1", "artist-1")
    val t2 = buildPlaylistTrack("t2", "artist-1")
    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"), TrackId("t2"))) } returns listOf(
      buildAppTrack("t1", "Song A", "artist-1", artistName = null),
      buildAppTrack("t2", "Song B", "artist-1", artistName = null),
    )

    val result = runner.run(userId, playlistId, buildPlaylist(t1, t2), null, emptyList())

    assertThat(result.succeeded).isFalse()
    assertThat(result.violations).containsExactlyInAnyOrder("artist-1 – Song A", "artist-1 – Song B")
  }

  @Test
  fun `run falls back to trackId when track not found in repository`() {
    val t1 = buildPlaylistTrack("t1", "artist-1")
    val t2 = buildPlaylistTrack("t2", "artist-1")
    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"), TrackId("t2"))) } returns emptyList()

    val result = runner.run(userId, playlistId, buildPlaylist(t1, t2), null, emptyList())

    assertThat(result.succeeded).isFalse()
    assertThat(result.violations).containsExactlyInAnyOrder("artist-1 – t1", "artist-1 – t2")
  }

  @Test
  fun `run skips tracks with no artistIds`() {
    val t1 = buildPlaylistTrack("t1")
    val t2 = buildPlaylistTrack("t2")
    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"), TrackId("t2"))) } returns emptyList()

    val result = runner.run(userId, playlistId, buildPlaylist(t1, t2), null, emptyList())

    assertThat(result.succeeded).isTrue()
    assertThat(result.violations).isEmpty()
  }

  @Test
  fun `canFix returns false`() {
    assertThat(runner.canFix()).isFalse()
  }
}
