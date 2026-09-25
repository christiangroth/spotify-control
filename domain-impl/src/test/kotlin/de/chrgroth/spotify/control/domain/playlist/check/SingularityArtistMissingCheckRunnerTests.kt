package de.chrgroth.spotify.control.domain.playlist.check

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Clock
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SingularityArtistMissingCheckRunnerTests {

  private val appArtistRepository: AppArtistRepositoryPort = mockk()
  private val runner = SingularityArtistMissingCheckRunner(appArtistRepository)

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
  fun `run returns no violations when included artist has a track on the playlist`() {
    val t1 = buildPlaylistTrack("t1", "artist-1")
    every { appArtistRepository.findAll() } returns listOf(buildArtist("artist-1", "Artist One", singularityIncluded = true))

    val result = runner.run(playlistId, buildPlaylist(t1), null, emptyList())

    assertThat(result.succeeded).isTrue()
    assertThat(result.violations).isEmpty()
    assertThat(result.checkId).isEqualTo("$playlistId:singularity-artist-missing")
  }

  @Test
  fun `run reports violation when included artist has no track on the playlist`() {
    every { appArtistRepository.findAll() } returns listOf(buildArtist("artist-1", "Artist One", singularityIncluded = true))

    val result = runner.run(playlistId, buildPlaylist(), null, emptyList())

    assertThat(result.succeeded).isFalse()
    assertThat(result.violations.map { it.id to it.message }).containsExactly("artist-1" to "Artist One")
  }

  @Test
  fun `run does not report excluded artists that are missing`() {
    every { appArtistRepository.findAll() } returns listOf(buildArtist("artist-1", "Artist One", singularityIncluded = false))

    val result = runner.run(playlistId, buildPlaylist(), null, emptyList())

    assertThat(result.succeeded).isTrue()
    assertThat(result.violations).isEmpty()
  }

  @Test
  fun `run violations are sorted alphabetically`() {
    every { appArtistRepository.findAll() } returns listOf(
      buildArtist("artist-1", "Zebra Artist", singularityIncluded = true),
      buildArtist("artist-2", "Alpha Artist", singularityIncluded = true),
    )

    val result = runner.run(playlistId, buildPlaylist(), null, emptyList())

    assertThat(result.succeeded).isFalse()
    assertThat(result.violations.map { it.message }).containsExactly("Alpha Artist", "Zebra Artist")
  }

  @Test
  fun `canFix returns false`() {
    assertThat(runner.canFix()).isFalse()
  }
}
