package de.chrgroth.spotify.control.domain.catalog

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AppAlbum
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.SyncCause
import de.chrgroth.spotify.control.domain.model.catalog.SyncTrace
import de.chrgroth.spotify.control.domain.model.catalog.SyncTraceEntityType
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.user.User
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.SyncTraceRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CatalogBrowserServiceTests {

  private val appArtistRepository: AppArtistRepositoryPort = mockk()
  private val appAlbumRepository: AppAlbumRepositoryPort = mockk()
  private val appTrackRepository: AppTrackRepositoryPort = mockk()
  private val syncTraceRepository: SyncTraceRepositoryPort = mockk()
  private val playlistRepository: PlaylistRepositoryPort = mockk()
  private val userRepository: UserRepositoryPort = mockk()

  private val service = CatalogBrowserService(
    appArtistRepository,
    appAlbumRepository,
    appTrackRepository,
    syncTraceRepository,
    playlistRepository,
    userRepository,
  )

  private val userId = UserId("user-1")
  private val triggeredAt = Instant.fromEpochSeconds(100)

  private fun buildUser() = User(
    spotifyUserId = userId,
    displayName = "User 1",
    encryptedAccessToken = "enc-access",
    encryptedRefreshToken = "enc-refresh",
    tokenExpiresAt = Instant.DISTANT_FUTURE,
    lastLoginAt = Instant.fromEpochSeconds(0),
  )

  @Test
  fun `getAlbums returns empty list when filter is blank`() {
    val result = service.getAlbums(" ")

    assertThat(result).isEmpty()
  }

  @Test
  fun `getAlbums matches album title case-insensitively across all artists and includes artist name`() {
    every { appAlbumRepository.findAll() } returns listOf(
      AppAlbum(id = AlbumId("album-1"), title = "Greatest Hits", artistName = "Artist One", lastSync = triggeredAt),
      AppAlbum(id = AlbumId("album-2"), title = "Other Record", artistName = "Artist Two", lastSync = triggeredAt),
    )
    every { appTrackRepository.findAll() } returns emptyList()

    val result = service.getAlbums("greatest")

    assertThat(result).hasSize(1)
    assertThat(result.first().albumId).isEqualTo("album-1")
    assertThat(result.first().title).isEqualTo("Greatest Hits")
    assertThat(result.first().artistName).isEqualTo("Artist One")
  }

  @Test
  fun `getArtistSyncTrace returns null when no trace recorded`() {
    every { syncTraceRepository.find(SyncTraceEntityType.ARTIST, "artist-1") } returns null

    val result = service.getArtistSyncTrace("artist-1")

    assertThat(result).isNull()
  }

  @Test
  fun `getArtistSyncTrace describes playback cause with resolved track name`() {
    every { syncTraceRepository.find(SyncTraceEntityType.ARTIST, "artist-1") } returns
      SyncTrace(SyncTraceEntityType.ARTIST, "artist-1", SyncCause.Playback("track-1"), triggeredAt)
    every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"))) } returns
      listOf(AppTrack(id = TrackId("track-1"), title = "Some Song", artistId = ArtistId("artist-1"), lastSync = triggeredAt))

    val result = service.getArtistSyncTrace("artist-1")

    assertThat(result).isNotNull
    assertThat(result!!.description).isEqualTo("Played track 'Some Song'")
    assertThat(result.triggeredAt).isEqualTo(triggeredAt)
  }

  @Test
  fun `getArtistSyncTrace falls back to raw track id when track unknown`() {
    every { syncTraceRepository.find(SyncTraceEntityType.ARTIST, "artist-1") } returns
      SyncTrace(SyncTraceEntityType.ARTIST, "artist-1", SyncCause.Playback("track-1"), triggeredAt)
    every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"))) } returns emptyList()

    val result = service.getArtistSyncTrace("artist-1")

    assertThat(result!!.description).isEqualTo("Played track 'track-1'")
  }

  @Test
  fun `getArtistSyncTrace describes playlist cause with resolved playlist and track names`() {
    every { syncTraceRepository.find(SyncTraceEntityType.ARTIST, "artist-1") } returns
      SyncTrace(SyncTraceEntityType.ARTIST, "artist-1", SyncCause.Playlist("playlist-1", "track-1"), triggeredAt)
    every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"))) } returns
      listOf(AppTrack(id = TrackId("track-1"), title = "Some Song", artistId = ArtistId("artist-1"), lastSync = triggeredAt))
    every { userRepository.findAll() } returns listOf(buildUser())
    every { playlistRepository.findByUserId(userId) } returns listOf(
      PlaylistInfo(
        spotifyPlaylistId = "playlist-1",
        snapshotId = "snap-1",
        lastSnapshotIdSyncTime = triggeredAt,
        name = "My Playlist",
        syncStatus = PlaylistSyncStatus.ACTIVE,
      ),
    )

    val result = service.getArtistSyncTrace("artist-1")

    assertThat(result!!.description).isEqualTo("Found in playlist 'My Playlist' via track 'Some Song'")
  }

  @Test
  fun `getArtistSyncTrace describes manual resync cause`() {
    every { syncTraceRepository.find(SyncTraceEntityType.ARTIST, "artist-1") } returns
      SyncTrace(SyncTraceEntityType.ARTIST, "artist-1", SyncCause.ManualResync, triggeredAt)

    val result = service.getArtistSyncTrace("artist-1")

    assertThat(result!!.description).isEqualTo("Manual or scheduled catalog resync")
  }

  @Test
  fun `getAlbumSyncTrace returns null when no trace recorded`() {
    every { syncTraceRepository.find(SyncTraceEntityType.ALBUM, "album-1") } returns null

    val result = service.getAlbumSyncTrace("album-1")

    assertThat(result).isNull()
  }

  @Test
  fun `getAlbumSyncTrace describes artist discography cause and resolves the artist's own trace`() {
    every { syncTraceRepository.find(SyncTraceEntityType.ALBUM, "album-1") } returns
      SyncTrace(SyncTraceEntityType.ALBUM, "album-1", SyncCause.ArtistDiscography("artist-1"), triggeredAt)
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns
      listOf(AppArtist(id = ArtistId("artist-1"), artistName = "Some Artist", lastSync = triggeredAt))
    every { syncTraceRepository.find(SyncTraceEntityType.ARTIST, "artist-1") } returns
      SyncTrace(SyncTraceEntityType.ARTIST, "artist-1", SyncCause.ManualResync, triggeredAt)

    val result = service.getAlbumSyncTrace("album-1")

    assertThat(result!!.description).isEqualTo(
      "Synced as part of full discography sync for artist 'Some Artist' (artist itself was synced because: Manual or scheduled catalog resync)",
    )
  }

  @Test
  fun `getAlbumSyncTrace omits artist trace detail when artist has no recorded trace`() {
    every { syncTraceRepository.find(SyncTraceEntityType.ALBUM, "album-1") } returns
      SyncTrace(SyncTraceEntityType.ALBUM, "album-1", SyncCause.ArtistDiscography("artist-1"), triggeredAt)
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns
      listOf(AppArtist(id = ArtistId("artist-1"), artistName = "Some Artist", lastSync = triggeredAt))
    every { syncTraceRepository.find(SyncTraceEntityType.ARTIST, "artist-1") } returns null

    val result = service.getAlbumSyncTrace("album-1")

    assertThat(result!!.description).isEqualTo("Synced as part of full discography sync for artist 'Some Artist'")
  }
}
