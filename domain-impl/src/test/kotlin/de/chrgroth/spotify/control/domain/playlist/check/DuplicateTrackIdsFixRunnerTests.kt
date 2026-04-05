package de.chrgroth.spotify.control.domain.playlist.check

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.PlaylistFixError
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.playlist.SpotifyPlaylistPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DuplicateTrackIdsFixRunnerTests {

  private val spotifyPlaylist: SpotifyPlaylistPort = mockk()
  private val runner = DuplicateTrackIdsFixRunner(spotifyPlaylist)

  private val userId = UserId("user-1")
  private val accessToken = AccessToken("token")
  private val playlistId = "playlist-1"

  private fun buildTrack(trackId: String) = PlaylistTrack(
    trackId = TrackId(trackId),
    artistIds = listOf(ArtistId("artist-1")),
    albumId = AlbumId("album-1"),
  )

  private fun buildPlaylist(tracks: List<PlaylistTrack>) = Playlist(
    spotifyPlaylistId = playlistId,
    tracks = tracks,
  )

  @Test
  fun `runFix returns success and makes no Spotify call when no duplicates`() {
    val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t2")))

    val result = runner.runFix(userId, accessToken, playlistId, playlist)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { spotifyPlaylist.removePlaylistTracks(any(), any(), any(), any()) }
  }

  @Test
  fun `runFix removes second occurrence when track appears twice`() {
    val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t2"), buildTrack("t1")))
    every { spotifyPlaylist.removePlaylistTracks(userId, accessToken, playlistId, mapOf("t1" to listOf(2))) } returns Unit.right()

    val result = runner.runFix(userId, accessToken, playlistId, playlist)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { spotifyPlaylist.removePlaylistTracks(userId, accessToken, playlistId, mapOf("t1" to listOf(2))) }
  }

  @Test
  fun `runFix removes all extra occurrences keeping only the first`() {
    val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t1"), buildTrack("t1")))
    every { spotifyPlaylist.removePlaylistTracks(userId, accessToken, playlistId, mapOf("t1" to listOf(1, 2))) } returns Unit.right()

    val result = runner.runFix(userId, accessToken, playlistId, playlist)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { spotifyPlaylist.removePlaylistTracks(userId, accessToken, playlistId, mapOf("t1" to listOf(1, 2))) }
  }

  @Test
  fun `runFix handles multiple different duplicate tracks`() {
    val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t2"), buildTrack("t1"), buildTrack("t2")))
    every {
      spotifyPlaylist.removePlaylistTracks(userId, accessToken, playlistId, mapOf("t1" to listOf(2), "t2" to listOf(3)))
    } returns Unit.right()

    val result = runner.runFix(userId, accessToken, playlistId, playlist)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) {
      spotifyPlaylist.removePlaylistTracks(userId, accessToken, playlistId, mapOf("t1" to listOf(2), "t2" to listOf(3)))
    }
  }

  @Test
  fun `runFix propagates error from Spotify`() {
    val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t1")))
    every { spotifyPlaylist.removePlaylistTracks(any(), any(), any(), any()) } returns PlaylistFixError.FIX_FAILED.left()

    val result = runner.runFix(userId, accessToken, playlistId, playlist)

    assertThat(result.isLeft()).isTrue()
    assertThat((result as Either.Left).value).isEqualTo(PlaylistFixError.FIX_FAILED)
  }
}
