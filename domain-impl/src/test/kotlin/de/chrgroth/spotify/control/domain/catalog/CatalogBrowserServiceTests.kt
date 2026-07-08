package de.chrgroth.spotify.control.domain.catalog

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AppAlbum
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.CatalogSyncEntityType
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
    every { appAlbumRepository.searchByTitle("greatest", CatalogBrowserService.SEARCH_RESULT_LIMIT) } returns listOf(
      AppAlbum(id = AlbumId("album-1"), title = "Greatest Hits", artistName = "Artist One", lastSync = triggeredAt),
    )
    every { appTrackRepository.findByAlbumIds(setOf(AlbumId("album-1"))) } returns emptyList()

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

  @Test
  fun `getSyncTimeline merges artists and albums by syncedAt descending and reports correct next offsets`() {
    val artistA = AppArtist(id = ArtistId("artist-a"), artistName = "Artist A", lastSync = Instant.fromEpochSeconds(100))
    val artistB = AppArtist(id = ArtistId("artist-b"), artistName = "Artist B", lastSync = Instant.fromEpochSeconds(90))
    val albumX = AppAlbum(
      id = AlbumId("album-x"), title = "Album X", artistId = ArtistId("artist-c"), artistName = "Artist C",
      totalTracks = 12, lastSync = Instant.fromEpochSeconds(95),
    )
    every { appArtistRepository.findRecentlySynced(0, 3) } returns listOf(artistA, artistB, AppArtist(id = ArtistId("artist-z"), artistName = "Z", lastSync = Instant.fromEpochSeconds(50)))
    every { appAlbumRepository.findRecentlySynced(0, 3) } returns listOf(albumX)
    every { appAlbumRepository.findByArtistIds(setOf(artistA.id, artistB.id)) } returns listOf(
      AppAlbum(id = AlbumId("album-a1"), artistId = artistA.id, lastSync = triggeredAt),
      AppAlbum(id = AlbumId("album-a2"), artistId = artistA.id, lastSync = triggeredAt),
    )

    val result = service.getSyncTimeline(artistOffset = 0, albumOffset = 0, limit = 2)

    assertThat(result.entries).hasSize(2)
    assertThat(result.entries[0].entityType).isEqualTo(CatalogSyncEntityType.ARTIST)
    assertThat(result.entries[0].artistId).isEqualTo("artist-a")
    assertThat(result.entries[0].albumCount).isEqualTo(2)
    assertThat(result.entries[1].entityType).isEqualTo(CatalogSyncEntityType.ALBUM)
    assertThat(result.entries[1].albumId).isEqualTo("album-x")
    assertThat(result.entries[1].artistId).isEqualTo("artist-c")
    assertThat(result.entries[1].trackCount).isEqualTo(12)
    assertThat(result.nextArtistOffset).isEqualTo(1)
    assertThat(result.nextAlbumOffset).isEqualTo(1)
    assertThat(result.hasMore).isTrue()
  }

  @Test
  fun `getSyncTimeline reports hasMore false when both sources are exhausted`() {
    every { appArtistRepository.findRecentlySynced(0, 3) } returns listOf(
      AppArtist(id = ArtistId("artist-a"), artistName = "Artist A", lastSync = Instant.fromEpochSeconds(100)),
    )
    every { appAlbumRepository.findRecentlySynced(0, 3) } returns emptyList()
    every { appAlbumRepository.findByArtistIds(setOf(ArtistId("artist-a"))) } returns emptyList()

    val result = service.getSyncTimeline(artistOffset = 0, albumOffset = 0, limit = 2)

    assertThat(result.entries).hasSize(1)
    assertThat(result.nextArtistOffset).isEqualTo(1)
    assertThat(result.nextAlbumOffset).isEqualTo(0)
    assertThat(result.hasMore).isFalse()
  }
}
