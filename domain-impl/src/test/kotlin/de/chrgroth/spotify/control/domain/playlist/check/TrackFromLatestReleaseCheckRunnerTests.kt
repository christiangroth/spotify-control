package de.chrgroth.spotify.control.domain.playlist.check

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.PlaylistFixError
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AppAlbum
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.SpotifyPlaylistPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TrackFromLatestReleaseCheckRunnerTests {

  private val appTrackRepository: AppTrackRepositoryPort = mockk()
  private val appAlbumRepository: AppAlbumRepositoryPort = mockk()
  private val spotifyPlaylist: SpotifyPlaylistPort = mockk()
  private val runner = TrackFromLatestReleaseCheckRunner(appTrackRepository, appAlbumRepository, spotifyPlaylist)

  private val userId = UserId("user-1")
  private val accessToken = AccessToken("token")
  private val playlistId = "playlist-1"
  private val artistId = ArtistId("artist-1")

  private fun buildPlaylistTrack(trackId: String, albumId: String? = null) = PlaylistTrack(
    trackId = TrackId(trackId),
    artistIds = listOf(artistId),
    albumId = albumId?.let { AlbumId(it) },
  )

  private fun buildAppTrack(trackId: String, title: String, albumId: String? = null, artistName: String? = "Artist A") = AppTrack(
    id = TrackId(trackId),
    title = title,
    albumId = albumId?.let { AlbumId(it) },
    artistId = artistId,
    artistName = artistName,
    lastSync = Instant.fromEpochMilliseconds(0),
  )

  private fun buildAppAlbum(albumId: String, type: String? = "album", releaseDate: String? = "2020-01-01", title: String? = albumId) = AppAlbum(
    id = AlbumId(albumId),
    type = type,
    releaseDate = releaseDate,
    title = title,
    lastSync = Instant.fromEpochMilliseconds(0),
  )

  private fun buildPlaylist(vararg tracks: PlaylistTrack) = Playlist(
    spotifyPlaylistId = playlistId,
    tracks = tracks.toList(),
  )

  @Test
  fun `run returns no violations for empty playlist`() {
    val result = runner.run(userId, playlistId, buildPlaylist(), null, emptyList())

    assertThat(result.succeeded).isTrue()
    assertThat(result.violations).isEmpty()
  }

  @Test
  fun `run returns no violations when track has only one candidate`() {
    val track = buildPlaylistTrack("t1", "album-1")
    val appTrack = buildAppTrack("t1", "Song A", "album-1")
    val album = buildAppAlbum("album-1")

    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"))) } returns listOf(appTrack)
    every { appAlbumRepository.findByArtistId(artistId) } returns listOf(album)
    every { appTrackRepository.findByAlbumId(AlbumId("album-1")) } returns listOf(appTrack)

    val result = runner.run(userId, playlistId, buildPlaylist(track), null, emptyList())

    assertThat(result.succeeded).isTrue()
    assertThat(result.violations).isEmpty()
  }

  @Test
  fun `run returns no violations when track is already on the best album`() {
    val track = buildPlaylistTrack("t1", "album-new")
    val appTrackOld = buildAppTrack("t1-old", "Song A", "album-old")
    val appTrackNew = buildAppTrack("t1", "Song A", "album-new")
    val albumOld = buildAppAlbum("album-old", releaseDate = "2019-01-01", title = "Old Album")
    val albumNew = buildAppAlbum("album-new", releaseDate = "2021-01-01", title = "New Album")

    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"))) } returns listOf(appTrackNew)
    every { appAlbumRepository.findByArtistId(artistId) } returns listOf(albumOld, albumNew)
    every { appTrackRepository.findByAlbumId(AlbumId("album-old")) } returns listOf(appTrackOld)
    every { appTrackRepository.findByAlbumId(AlbumId("album-new")) } returns listOf(appTrackNew)

    val result = runner.run(userId, playlistId, buildPlaylist(track), null, emptyList())

    assertThat(result.succeeded).isTrue()
    assertThat(result.violations).isEmpty()
  }

  @Test
  fun `run reports violation when newer album release exists`() {
    val track = buildPlaylistTrack("t1", "album-old")
    val appTrackOld = buildAppTrack("t1", "Song A", "album-old")
    val appTrackNew = buildAppTrack("t2", "Song A", "album-new")
    val albumOld = buildAppAlbum("album-old", releaseDate = "2019-01-01", title = "Old Album")
    val albumNew = buildAppAlbum("album-new", releaseDate = "2021-01-01", title = "New Album")

    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"))) } returns listOf(appTrackOld)
    every { appAlbumRepository.findByArtistId(artistId) } returns listOf(albumOld, albumNew)
    every { appTrackRepository.findByAlbumId(AlbumId("album-old")) } returns listOf(appTrackOld)
    every { appTrackRepository.findByAlbumId(AlbumId("album-new")) } returns listOf(appTrackNew)

    val result = runner.run(userId, playlistId, buildPlaylist(track), null, emptyList())

    assertThat(result.succeeded).isFalse()
    assertThat(result.violations).containsExactly("Artist A – Song A (Old Album → New Album)")
    assertThat(result.checkId).isEqualTo("$playlistId:track-from-latest-release")
  }

  @Test
  fun `run prefers album type over single`() {
    val track = buildPlaylistTrack("t1", "single-album")
    val appTrackOnSingle = buildAppTrack("t1", "Song A", "single-album")
    val appTrackOnAlbum = buildAppTrack("t2", "Song A", "full-album")
    val singleAlbum = buildAppAlbum("single-album", type = "single", releaseDate = "2022-01-01", title = "Single Release")
    val fullAlbum = buildAppAlbum("full-album", type = "album", releaseDate = "2020-01-01", title = "Full Album")

    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"))) } returns listOf(appTrackOnSingle)
    every { appAlbumRepository.findByArtistId(artistId) } returns listOf(singleAlbum, fullAlbum)
    every { appTrackRepository.findByAlbumId(AlbumId("single-album")) } returns listOf(appTrackOnSingle)
    every { appTrackRepository.findByAlbumId(AlbumId("full-album")) } returns listOf(appTrackOnAlbum)

    val result = runner.run(userId, playlistId, buildPlaylist(track), null, emptyList())

    assertThat(result.succeeded).isFalse()
    assertThat(result.violations).containsExactly("Artist A – Song A (Single Release → Full Album)")
  }

  @Test
  fun `run handles year-only release dates by padding to January 1st`() {
    val track = buildPlaylistTrack("t1", "album-a")
    val appTrackA = buildAppTrack("t1", "Song A", "album-a")
    val appTrackB = buildAppTrack("t2", "Song A", "album-b")
    val albumA = buildAppAlbum("album-a", releaseDate = "2019", title = "Album A")
    val albumB = buildAppAlbum("album-b", releaseDate = "2020-06-15", title = "Album B")

    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"))) } returns listOf(appTrackA)
    every { appAlbumRepository.findByArtistId(artistId) } returns listOf(albumA, albumB)
    every { appTrackRepository.findByAlbumId(AlbumId("album-a")) } returns listOf(appTrackA)
    every { appTrackRepository.findByAlbumId(AlbumId("album-b")) } returns listOf(appTrackB)

    val result = runner.run(userId, playlistId, buildPlaylist(track), null, emptyList())

    assertThat(result.succeeded).isFalse()
    assertThat(result.violations).containsExactly("Artist A – Song A (Album A → Album B)")
  }

  @Test
  fun `run handles year-month release dates by padding to first day of month`() {
    val track = buildPlaylistTrack("t1", "album-a")
    val appTrackA = buildAppTrack("t1", "Song A", "album-a")
    val appTrackB = buildAppTrack("t2", "Song A", "album-b")
    val albumA = buildAppAlbum("album-a", releaseDate = "2020-01", title = "Album A")
    val albumB = buildAppAlbum("album-b", releaseDate = "2020-01-15", title = "Album B")

    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"))) } returns listOf(appTrackA)
    every { appAlbumRepository.findByArtistId(artistId) } returns listOf(albumA, albumB)
    every { appTrackRepository.findByAlbumId(AlbumId("album-a")) } returns listOf(appTrackA)
    every { appTrackRepository.findByAlbumId(AlbumId("album-b")) } returns listOf(appTrackB)

    val result = runner.run(userId, playlistId, buildPlaylist(track), null, emptyList())

    assertThat(result.succeeded).isFalse()
    assertThat(result.violations).containsExactly("Artist A – Song A (Album A → Album B)")
  }

  @Test
  fun `run falls back to unknown artist and album when names are null`() {
    val track = buildPlaylistTrack("t1", "album-old")
    val appTrackOld = buildAppTrack("t1", "Song A", "album-old", artistName = null)
    val appTrackNew = buildAppTrack("t2", "Song A", "album-new")
    val albumOld = buildAppAlbum("album-old", releaseDate = "2019-01-01", title = null)
    val albumNew = buildAppAlbum("album-new", releaseDate = "2021-01-01", title = null)

    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"))) } returns listOf(appTrackOld)
    every { appAlbumRepository.findByArtistId(artistId) } returns listOf(albumOld, albumNew)
    every { appTrackRepository.findByAlbumId(AlbumId("album-old")) } returns listOf(appTrackOld)
    every { appTrackRepository.findByAlbumId(AlbumId("album-new")) } returns listOf(appTrackNew)

    val result = runner.run(userId, playlistId, buildPlaylist(track), null, emptyList())

    assertThat(result.succeeded).isFalse()
    assertThat(result.violations).containsExactly("Unknown Artist – Song A (Unknown Album → Unknown Album)")
  }

  @Test
  fun `canFix returns true`() {
    assertThat(runner.canFix()).isTrue()
  }

  @Test
  fun `fix returns success and makes no Spotify call when no violations`() {
    val track = buildPlaylistTrack("t1", "album-1")
    val appTrack = buildAppTrack("t1", "Song A", "album-1")
    val album = buildAppAlbum("album-1")

    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"))) } returns listOf(appTrack)
    every { appAlbumRepository.findByArtistId(artistId) } returns listOf(album)
    every { appTrackRepository.findByAlbumId(AlbumId("album-1")) } returns listOf(appTrack)

    val result = runner.fix(userId, accessToken, playlistId, buildPlaylist(track), null, emptyList())

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { spotifyPlaylist.replacePlaylistTrack(any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `fix replaces outdated track`() {
    val track = buildPlaylistTrack("t1", "album-old")
    val appTrackOld = buildAppTrack("t1", "Song A", "album-old")
    val appTrackNew = buildAppTrack("t2", "Song A", "album-new")
    val albumOld = buildAppAlbum("album-old", releaseDate = "2019-01-01")
    val albumNew = buildAppAlbum("album-new", releaseDate = "2021-01-01")

    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"))) } returns listOf(appTrackOld)
    every { appAlbumRepository.findByArtistId(artistId) } returns listOf(albumOld, albumNew)
    every { appTrackRepository.findByAlbumId(AlbumId("album-old")) } returns listOf(appTrackOld)
    every { appTrackRepository.findByAlbumId(AlbumId("album-new")) } returns listOf(appTrackNew)
    every { spotifyPlaylist.replacePlaylistTrack(userId, accessToken, playlistId, "t1", "t2", 0) } returns Unit.right()

    val result = runner.fix(userId, accessToken, playlistId, buildPlaylist(track), null, emptyList())

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { spotifyPlaylist.replacePlaylistTrack(userId, accessToken, playlistId, "t1", "t2", 0) }
  }

  @Test
  fun `fix processes multiple violations in reverse position order`() {
    val track0 = buildPlaylistTrack("t1", "album-old")
    val track1 = buildPlaylistTrack("t3", "album-current")
    val track2 = buildPlaylistTrack("t5", "album-old")
    val appTrackOld1 = buildAppTrack("t1", "Song A", "album-old")
    val appTrackNew1 = buildAppTrack("t2", "Song A", "album-new")
    val appTrackCurrent = buildAppTrack("t3", "Song B", "album-current")
    val appTrackOld2 = buildAppTrack("t5", "Song C", "album-old")
    val appTrackNew2 = buildAppTrack("t6", "Song C", "album-new")
    val albumOld = buildAppAlbum("album-old", releaseDate = "2019-01-01")
    val albumNew = buildAppAlbum("album-new", releaseDate = "2021-01-01")
    val albumCurrent = buildAppAlbum("album-current")

    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"), TrackId("t3"), TrackId("t5"))) } returns
      listOf(appTrackOld1, appTrackCurrent, appTrackOld2)
    every { appAlbumRepository.findByArtistId(artistId) } returns listOf(albumOld, albumNew, albumCurrent)
    every { appTrackRepository.findByAlbumId(AlbumId("album-old")) } returns listOf(appTrackOld1, appTrackOld2)
    every { appTrackRepository.findByAlbumId(AlbumId("album-new")) } returns listOf(appTrackNew1, appTrackNew2)
    every { appTrackRepository.findByAlbumId(AlbumId("album-current")) } returns listOf(appTrackCurrent)
    every { spotifyPlaylist.replacePlaylistTrack(userId, accessToken, playlistId, "t5", "t6", 2) } returns Unit.right()
    every { spotifyPlaylist.replacePlaylistTrack(userId, accessToken, playlistId, "t1", "t2", 0) } returns Unit.right()

    val result = runner.fix(userId, accessToken, playlistId, buildPlaylist(track0, track1, track2), null, emptyList())

    assertThat(result.isRight()).isTrue()
    verifyOrder {
      spotifyPlaylist.replacePlaylistTrack(userId, accessToken, playlistId, "t5", "t6", 2)
      spotifyPlaylist.replacePlaylistTrack(userId, accessToken, playlistId, "t1", "t2", 0)
    }
  }

  @Test
  fun `fix propagates error from replacePlaylistTrack and stops processing`() {
    val track0 = buildPlaylistTrack("t1", "album-old")
    val track1 = buildPlaylistTrack("t3", "album-old")
    val appTrackOld1 = buildAppTrack("t1", "Song A", "album-old")
    val appTrackNew1 = buildAppTrack("t2", "Song A", "album-new")
    val appTrackOld2 = buildAppTrack("t3", "Song B", "album-old")
    val appTrackNew2 = buildAppTrack("t4", "Song B", "album-new")
    val albumOld = buildAppAlbum("album-old", releaseDate = "2019-01-01")
    val albumNew = buildAppAlbum("album-new", releaseDate = "2021-01-01")

    every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"), TrackId("t3"))) } returns listOf(appTrackOld1, appTrackOld2)
    every { appAlbumRepository.findByArtistId(artistId) } returns listOf(albumOld, albumNew)
    every { appTrackRepository.findByAlbumId(AlbumId("album-old")) } returns listOf(appTrackOld1, appTrackOld2)
    every { appTrackRepository.findByAlbumId(AlbumId("album-new")) } returns listOf(appTrackNew1, appTrackNew2)
    every { spotifyPlaylist.replacePlaylistTrack(userId, accessToken, playlistId, "t3", "t4", 1) } returns PlaylistFixError.FIX_FAILED.left()

    val result = runner.fix(userId, accessToken, playlistId, buildPlaylist(track0, track1), null, emptyList())

    assertThat(result.isLeft()).isTrue()
    assertThat((result as Either.Left).value).isEqualTo(PlaylistFixError.FIX_FAILED)
    verify(exactly = 0) { spotifyPlaylist.replacePlaylistTrack(userId, accessToken, playlistId, "t1", "t2", 0) }
  }
}
