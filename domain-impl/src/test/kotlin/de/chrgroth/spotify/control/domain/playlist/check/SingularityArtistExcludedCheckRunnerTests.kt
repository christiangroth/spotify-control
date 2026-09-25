package de.chrgroth.spotify.control.domain.playlist.check

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Clock
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SingularityArtistExcludedCheckRunnerTests {

  private val appArtistRepository: AppArtistRepositoryPort = mockk()
  private val appTrackRepository: AppTrackRepositoryPort = mockk()
  private val runner = SingularityArtistExcludedCheckRunner(appArtistRepository, appTrackRepository)

  private val playlistId = "playlist-1"

  private fun buildPlaylistTrack(trackId: String, vararg artistIds: String, albumId: String? = null) = PlaylistTrack(
    trackId = TrackId(trackId),
    artistIds = artistIds.map { ArtistId(it) },
    albumId = albumId?.let { AlbumId(it) },
  )

  private fun buildArtist(artistId: String, name: String, singularityIncluded: Boolean) = AppArtist(
    id = ArtistId(artistId),
    artistName = name,
    lastSync = Instant.fromEpochMilliseconds(0),
    singularityIncluded = singularityIncluded,
  )

  private fun buildAppTrack(trackId: String, title: String, artistId: String) = AppTrack(
    id = TrackId(trackId),
    title = title,
    artistId = ArtistId(artistId),
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
    assertThat(runner.isApplicable(buildPlaylistInfo(PlaylistType.SINGULARITY_STAGING))).isFalse()
    assertThat(runner.isApplicable(buildPlaylistInfo(PlaylistType.UNKNOWN))).isFalse()
  }

  @Test
  fun `run returns no violations for empty playlist`() {
    val result = runner.run(playlistId, buildPlaylist(), null, emptyList())

    assertThat(result.succeeded).isTrue()
    assertThat(result.violations).isEmpty()
  }

  @Test
  fun `run returns no violations when the artist is included`() {
    val t1 = buildPlaylistTrack("t1", "artist-1")
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns
      listOf(buildArtist("artist-1", "Artist One", singularityIncluded = true))

    val result = runner.run(playlistId, buildPlaylist(t1), null, emptyList())

    assertThat(result.succeeded).isTrue()
    assertThat(result.violations).isEmpty()
    assertThat(result.checkId).isEqualTo("$playlistId:singularity-artist-excluded")
  }

  @Test
  fun `run reports violation when the artist is excluded but has a track on the playlist`() {
    val t1 = buildPlaylistTrack("t1", "artist-1")
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns
      listOf(buildArtist("artist-1", "Artist One", singularityIncluded = false))
    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"))) } returns
      listOf(buildAppTrack("t1", "Song A", "artist-1"))

    val result = runner.run(playlistId, buildPlaylist(t1), null, emptyList())

    assertThat(result.succeeded).isFalse()
    assertThat(result.violations.map { it.id to it.message }).containsExactly("t1" to "Artist One – Song A")
  }

  @Test
  fun `run does not report tracks for unknown artists`() {
    val t1 = buildPlaylistTrack("t1", "artist-1")
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns emptyList()

    val result = runner.run(playlistId, buildPlaylist(t1), null, emptyList())

    assertThat(result.succeeded).isTrue()
    assertThat(result.violations).isEmpty()
  }

  @Test
  fun `run falls back to ids when artist name or track title are unavailable`() {
    val t1 = buildPlaylistTrack("t1", "artist-1")
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns
      listOf(buildArtist("artist-1", "Artist One", singularityIncluded = false))
    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"))) } returns emptyList()

    val result = runner.run(playlistId, buildPlaylist(t1), null, emptyList())

    assertThat(result.succeeded).isFalse()
    assertThat(result.violations.map { it.message }).containsExactly("Artist One – t1")
  }

  @Test
  fun `run violations are sorted alphabetically`() {
    val t1 = buildPlaylistTrack("t1", "artist-1")
    val t2 = buildPlaylistTrack("t2", "artist-2")
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"), ArtistId("artist-2"))) } returns listOf(
      buildArtist("artist-1", "Zebra Artist", singularityIncluded = false),
      buildArtist("artist-2", "Alpha Artist", singularityIncluded = false),
    )
    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"), TrackId("t2"))) } returns listOf(
      buildAppTrack("t1", "Song A", "artist-1"),
      buildAppTrack("t2", "Song B", "artist-2"),
    )

    val result = runner.run(playlistId, buildPlaylist(t1, t2), null, emptyList())

    assertThat(result.succeeded).isFalse()
    assertThat(result.violations.map { it.message }).containsExactly(
      "Alpha Artist – Song B",
      "Zebra Artist – Song A",
    )
  }

  @Test
  fun `canFix returns false`() {
    assertThat(runner.canFix()).isFalse()
  }
}
