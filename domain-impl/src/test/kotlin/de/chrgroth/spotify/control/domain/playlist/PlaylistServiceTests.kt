package de.chrgroth.spotify.control.domain.playlist

import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.PlaylistSyncError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.SyncCause
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTracksPage
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.model.playlist.SpotifyPlaylistItem
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.model.playlist.MissingArtist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSettingsEntry
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSettingsView
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.SpotifyCatalogPort
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.readmodel.PlaylistSettingsViewRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistSyncNotificationPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.playlist.SpotifyPlaylistPort
import de.chrgroth.spotify.control.domain.catalog.SyncController
import de.chrgroth.spotify.control.domain.catalog.CatalogSyncRequest
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogPort
import de.chrgroth.spotify.control.domain.user.CurrentUserResolver
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@Suppress("LargeClass")
class PlaylistServiceTests {

  private val currentUserResolver: CurrentUserResolver = mockk()
  private val playlistRepository: PlaylistRepositoryPort = mockk()
  private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
  private val spotifyPlaylist: SpotifyPlaylistPort = mockk()
  private val outboxPort: OutboxPort = mockk()
  private val dashboardRefresh: DashboardRefreshPort = mockk()
  private val playlistCheckRepository: AppPlaylistCheckRepositoryPort = mockk()
  private val syncController: SyncController = mockk(relaxed = true)
  private val catalogPort: CatalogPort = mockk(relaxed = true)
  private val appArtistRepository: AppArtistRepositoryPort = mockk()
  private val spotifyCatalog: SpotifyCatalogPort = mockk()
  private val meterRegistry = SimpleMeterRegistry()
  private val syncNotification: PlaylistSyncNotificationPort = mockk(relaxed = true)
  private val playlistSettingsViewRepository: PlaylistSettingsViewRepositoryPort = mockk(relaxed = true)

  private val adapter = PlaylistService(
    currentUserResolver, playlistRepository,
    spotifyAccessToken, spotifyPlaylist,
    outboxPort, dashboardRefresh,
    playlistCheckRepository,
    syncController,
    catalogPort,
    appArtistRepository,
    spotifyCatalog,
    meterRegistry,
    syncNotification,
    playlistSettingsViewRepository,
  )

  private val userId = UserId("user-1")
  private val accessToken = AccessToken("access-token")
  private val now = Clock.System.now()

  private fun buildPlaylistInfo(
    id: String,
    snapshotId: String = "snap-1",
    syncStatus: PlaylistSyncStatus = PlaylistSyncStatus.ACTIVE,
    name: String = "Playlist $id",
  ) = PlaylistInfo(
    spotifyPlaylistId = id,
    snapshotId = snapshotId,
    lastSnapshotIdSyncTime = now - 1.hours,
    name = name,
    syncStatus = syncStatus,
  )

  private fun buildSpotifyItem(id: String, snapshotId: String = "snap-1", ownerId: String = "user-1") = SpotifyPlaylistItem(
    id = id,
    name = "Playlist $id",
    snapshotId = snapshotId,
    ownerId = ownerId,
  )

  // --- getMissingArtists tests ---

  private fun buildPlaylistWithArtists(playlistId: String, vararg artistIds: String) = Playlist(
    spotifyPlaylistId = playlistId,
    tracks = artistIds.mapIndexed { index, artistId ->
      PlaylistTrack(trackId = TrackId("t${index + 1}"), artistIds = listOf(ArtistId(artistId)), albumId = null)
    },
  )

  @Test
  fun `getMissingArtists returns empty list when no user exists`() {
    every { currentUserResolver.userId() } returns null

    val result = adapter.getMissingArtists("p1")

    assertThat(result).isEmpty()
    verify(exactly = 0) { playlistRepository.findByPlaylistId(any()) }
  }

  @Test
  fun `getMissingArtists returns empty list when playlist is unknown`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findByPlaylistId("p2") } returns null

    val result = adapter.getMissingArtists("p2")

    assertThat(result).isEmpty()
  }

  @Test
  fun `getMissingArtists returns empty list when all artists are already in catalog`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findByPlaylistId("p1") } returns buildPlaylistWithArtists("p1", "artist-1")
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns listOf(
      AppArtist(id = ArtistId("artist-1"), artistName = "Artist 1", lastSync = now),
    )

    val result = adapter.getMissingArtists("p1")

    assertThat(result).isEmpty()
    verify(exactly = 0) { spotifyAccessToken.getValidAccessToken() }
  }

  @Test
  fun `getMissingArtists resolves names for missing artists via Spotify and sorts by name`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findByPlaylistId("p1") } returns buildPlaylistWithArtists("p1", "artist-1", "artist-2")
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"), ArtistId("artist-2"))) } returns emptyList()
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyCatalog.getArtists(accessToken, listOf("artist-1", "artist-2")) } returns listOf(
      AppArtist(id = ArtistId("artist-1"), artistName = "Zeta", lastSync = now),
      AppArtist(id = ArtistId("artist-2"), artistName = "Alpha", lastSync = now),
    ).right()

    val result = adapter.getMissingArtists("p1")

    assertThat(result).containsExactly(
      MissingArtist(id = "artist-2", name = "Alpha"),
      MissingArtist(id = "artist-1", name = "Zeta"),
    )
  }

  @Test
  fun `getMissingArtists only checks the main artist per track, ignoring featured artists`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findByPlaylistId("p1") } returns Playlist(
      spotifyPlaylistId = "p1",
      tracks = listOf(PlaylistTrack(trackId = TrackId("t1"), artistIds = listOf(ArtistId("artist-1"), ArtistId("artist-2")), albumId = null)),
    )
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns emptyList()
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyCatalog.getArtists(accessToken, listOf("artist-1")) } returns listOf(
      AppArtist(id = ArtistId("artist-1"), artistName = "Main", lastSync = now),
    ).right()

    val result = adapter.getMissingArtists("p1")

    assertThat(result).containsExactly(MissingArtist(id = "artist-1", name = "Main"))
    verify(exactly = 1) { spotifyCatalog.getArtists(accessToken, listOf("artist-1")) }
  }

  @Test
  fun `getMissingArtists falls back to null name when Spotify lookup fails`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findByPlaylistId("p1") } returns buildPlaylistWithArtists("p1", "artist-1")
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns emptyList()
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyCatalog.getArtists(accessToken, listOf("artist-1")) } returns SpotifyRateLimitError(1.seconds).left()

    val result = adapter.getMissingArtists("p1")

    assertThat(result).containsExactly(MissingArtist(id = "artist-1", name = null))
  }

  // --- enqueueUpdates tests ---

  @Test
  fun `enqueueUpdates does nothing when no user exists`() {
    every { currentUserResolver.userId() } returns null

    adapter.enqueueUpdates()

    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `enqueueUpdates enqueues task for the stored user`() {
    every { currentUserResolver.userId() } returns UserId("user-1")
    every { outboxPort.enqueue(any()) } just runs

    adapter.enqueueUpdates()

    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistInfo()) }
  }

  // --- syncPlaylists tests ---

  @Test
  fun `syncPlaylists skips when user not found`() {
    every { currentUserResolver.userId() } returns null

    val result = adapter.syncPlaylists()

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { spotifyPlaylist.getPlaylists(any()) }
  }

  @Test
  fun `syncPlaylists filters out playlists not owned by user`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylists(accessToken) } returns listOf(
      buildSpotifyItem("p1", ownerId = "user-1"),
      buildSpotifyItem("p2", ownerId = "other-user"),
    ).right()
    every { playlistRepository.findAll() } returns emptyList()
    every { playlistRepository.replaceAll(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistMetadata() } just runs
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.syncPlaylists()

    assertThat(result.isRight()).isTrue()
    val savedSlot = slot<List<PlaylistInfo>>()
    verify { playlistRepository.replaceAll(capture(savedSlot)) }
    assertThat(savedSlot.captured).hasSize(1)
    assertThat(savedSlot.captured[0].spotifyPlaylistId).isEqualTo("p1")
  }

  @Test
  fun `syncPlaylists persists new playlists with PASSIVE status`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylists(accessToken) } returns listOf(buildSpotifyItem("p1")).right()
    every { playlistRepository.findAll() } returns emptyList()
    every { playlistRepository.replaceAll(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistMetadata() } just runs
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.syncPlaylists()

    assertThat(result.isRight()).isTrue()
    val savedSlot = slot<List<PlaylistInfo>>()
    verify { playlistRepository.replaceAll(capture(savedSlot)) }
    assertThat(savedSlot.captured).hasSize(1)
    assertThat(savedSlot.captured[0].spotifyPlaylistId).isEqualTo("p1")
    assertThat(savedSlot.captured[0].syncStatus).isEqualTo(PlaylistSyncStatus.PASSIVE)
  }

  @Test
  fun `syncPlaylists preserves existing syncStatus`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylists(accessToken) } returns listOf(buildSpotifyItem("p1")).right()
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.PASSIVE))
    every { playlistRepository.replaceAll(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs

    adapter.syncPlaylists()

    val savedSlot = slot<List<PlaylistInfo>>()
    verify { playlistRepository.replaceAll(capture(savedSlot)) }
    assertThat(savedSlot.captured[0].syncStatus).isEqualTo(PlaylistSyncStatus.PASSIVE)
  }

  @Test
  fun `syncPlaylists uses latest playlist state after Spotify API call to preserve syncStatus changes made in the meantime`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylists(accessToken) } returns listOf(buildSpotifyItem("p1")).right()
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.ACTIVE))
    every { playlistRepository.replaceAll(any()) } just runs
    every { playlistRepository.findByPlaylistId("p1") } returns mockk()
    every { outboxPort.enqueue(any()) } just runs

    adapter.syncPlaylists()

    val savedSlot = slot<List<PlaylistInfo>>()
    verify { playlistRepository.replaceAll(capture(savedSlot)) }
    assertThat(savedSlot.captured[0].syncStatus).isEqualTo(PlaylistSyncStatus.ACTIVE)
  }

  @Test
  fun `syncPlaylists preserves lastSnapshotIdSyncTime when snapshotId is unchanged`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylists(accessToken) } returns listOf(buildSpotifyItem("p1", snapshotId = "snap-1")).right()
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1", snapshotId = "snap-1"))
    every { playlistRepository.replaceAll(any()) } just runs
    every { playlistRepository.findByPlaylistId("p1") } returns mockk()
    every { outboxPort.enqueue(any()) } just runs

    adapter.syncPlaylists()

    val savedSlot = slot<List<PlaylistInfo>>()
    verify { playlistRepository.replaceAll(capture(savedSlot)) }
    assertThat(savedSlot.captured[0].lastSnapshotIdSyncTime).isEqualTo(now - 1.hours)
  }

  @Test
  fun `syncPlaylists updates lastSnapshotIdSyncTime when snapshotId changes`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylists(accessToken) } returns listOf(buildSpotifyItem("p1", snapshotId = "snap-2")).right()
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1", snapshotId = "snap-1"))
    every { playlistRepository.replaceAll(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs

    adapter.syncPlaylists()

    val savedSlot = slot<List<PlaylistInfo>>()
    verify { playlistRepository.replaceAll(capture(savedSlot)) }
    assertThat(savedSlot.captured[0].lastSnapshotIdSyncTime).isGreaterThan(now - 1.hours)
  }

  @Test
  fun `syncPlaylists notifies dashboard when playlist count increases`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylists(accessToken) } returns listOf(buildSpotifyItem("p1"), buildSpotifyItem("p2")).right()
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1"))
    every { playlistRepository.replaceAll(any()) } just runs
    every { playlistRepository.findByPlaylistId("p1") } returns mockk()
    every { outboxPort.enqueue(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistMetadata() } just runs

    val result = adapter.syncPlaylists()

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { dashboardRefresh.notifyUserPlaylistMetadata() }
  }

  @Test
  fun `syncPlaylists notifies dashboard when playlist count decreases`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylists(accessToken) } returns listOf(buildSpotifyItem("p1")).right()
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1"), buildPlaylistInfo("p2"))
    every { playlistRepository.replaceAll(any()) } just runs
    every { playlistRepository.findByPlaylistId("p1") } returns mockk()
    every { dashboardRefresh.notifyUserPlaylistMetadata() } just runs
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.syncPlaylists()

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { dashboardRefresh.notifyUserPlaylistMetadata() }
  }

  @Test
  fun `syncPlaylists does not notify dashboard when playlist count is unchanged`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylists(accessToken) } returns listOf(buildSpotifyItem("p1")).right()
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1"))
    every { playlistRepository.replaceAll(any()) } just runs
    every { playlistRepository.findByPlaylistId("p1") } returns mockk()
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.syncPlaylists()

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { dashboardRefresh.notifyUserPlaylistMetadata() }
  }

  @Test
  fun `syncPlaylists returns Left when spotify fetch fails`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylists(accessToken) } returns PlaylistSyncError.PLAYLIST_FETCH_FAILED.left()

    val result = adapter.syncPlaylists()

    assertThat(result.isLeft()).isTrue()
    verify(exactly = 0) { playlistRepository.replaceAll(any()) }
  }

  @Test
  fun `syncPlaylists notifies sync failure when spotify fetch fails`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylists(accessToken) } returns PlaylistSyncError.PLAYLIST_FETCH_FAILED.left()

    adapter.syncPlaylists()

    verify(exactly = 1) { syncNotification.notifySyncFailed(PlaylistSyncError.PLAYLIST_FETCH_FAILED.code) }
  }

  @Test
  fun `syncPlaylists returns Left with SpotifyRateLimitError when rate limited`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylists(accessToken) } returns SpotifyRateLimitError(30.seconds).left()

    val result = adapter.syncPlaylists()

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isInstanceOf(SpotifyRateLimitError::class.java)
  }

  @Test
  fun `syncPlaylists does not notify sync failure when rate limited`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylists(accessToken) } returns SpotifyRateLimitError(30.seconds).left()

    adapter.syncPlaylists()

    verify(exactly = 0) { syncNotification.notifySyncFailed(any()) }
  }

  // --- updateSyncStatus tests ---

  @Test
  fun `updateSyncStatus returns Left when user not found`() {
    every { currentUserResolver.userId() } returns null

    val result = adapter.updateSyncStatus("p1", PlaylistSyncStatus.PASSIVE)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(PlaylistSyncError.PLAYLIST_NOT_FOUND)
  }

  @Test
  fun `updateSyncStatus returns Left when playlist not found`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1"))

    val result = adapter.updateSyncStatus("p-unknown", PlaylistSyncStatus.PASSIVE)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(PlaylistSyncError.PLAYLIST_NOT_FOUND)
  }

  @Test
  fun `updateSyncStatus updates only the target playlist`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns listOf(
      buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.ACTIVE),
      buildPlaylistInfo("p2", syncStatus = PlaylistSyncStatus.ACTIVE),
    )
    every { playlistRepository.replaceAll(any()) } just runs
    every { playlistRepository.findByPlaylistId("p1") } returns mockk()
    every { dashboardRefresh.notifyUserPlaylistMetadata() } just runs
    every { playlistCheckRepository.deleteByPlaylistId("p1") } just runs
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.updateSyncStatus("p1", PlaylistSyncStatus.PASSIVE)

    assertThat(result.isRight()).isTrue()
    val savedSlot = slot<List<PlaylistInfo>>()
    verify { playlistRepository.replaceAll(capture(savedSlot)) }
    val updated = savedSlot.captured.associateBy { it.spotifyPlaylistId }
    assertThat(updated["p1"]!!.syncStatus).isEqualTo(PlaylistSyncStatus.PASSIVE)
    assertThat(updated["p2"]!!.syncStatus).isEqualTo(PlaylistSyncStatus.ACTIVE)
  }

  @Test
  fun `syncPlaylists enqueues SyncPlaylistData for active playlist with changed snapshotId`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylists(accessToken) } returns listOf(buildSpotifyItem("p1", snapshotId = "snap-2")).right()
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1", snapshotId = "snap-1"))
    every { playlistRepository.replaceAll(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs

    adapter.syncPlaylists()

    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData("p1")) }
  }

  @Test
  fun `syncPlaylists enqueues SyncPlaylistData for active playlist with no existing playlist data`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylists(accessToken) } returns listOf(buildSpotifyItem("p1")).right()
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1"))
    every { playlistRepository.replaceAll(any()) } just runs
    every { playlistRepository.findByPlaylistId("p1") } returns null
    every { outboxPort.enqueue(any()) } just runs

    adapter.syncPlaylists()

    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData("p1")) }
  }

  @Test
  fun `syncPlaylists does not enqueue SyncPlaylistData for passive playlist`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylists(accessToken) } returns listOf(buildSpotifyItem("p1")).right()
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.PASSIVE))
    every { playlistRepository.replaceAll(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs

    adapter.syncPlaylists()

    verify(exactly = 0) { outboxPort.enqueue(any<DomainOutboxEvent.SyncPlaylistData>()) }
  }

  @Test
  fun `syncPlaylists does not enqueue SyncPlaylistData for active playlist with unchanged snapshotId and existing data`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylists(accessToken) } returns listOf(buildSpotifyItem("p1")).right()
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1"))
    every { playlistRepository.replaceAll(any()) } just runs
    every { playlistRepository.findByPlaylistId("p1") } returns mockk()
    every { outboxPort.enqueue(any()) } just runs

    adapter.syncPlaylists()

    verify(exactly = 0) { outboxPort.enqueue(any<DomainOutboxEvent.SyncPlaylistData>()) }
  }

  // --- syncPlaylistData tests ---

  private val singleTrack = PlaylistTrack(
    trackId = TrackId("track-1"),
    artistIds = listOf(ArtistId("artist-1")),
    albumId = AlbumId("album-1"),
  )

  private fun buildPlaylist(id: String) = Playlist(
    spotifyPlaylistId = id,
    tracks = listOf(singleTrack),
  )

  private fun buildTracksPage(tracks: List<PlaylistTrack> = listOf(singleTrack), snapshotId: String = "snap-1", nextUrl: String? = null) =
    de.chrgroth.spotify.control.domain.model.playlist.PlaylistTracksPage(
      snapshotId = snapshotId,
      tracks = tracks,
      nextUrl = nextUrl,
    )

  @Test
  fun `syncPlaylistData skips when user not found`() {
    every { currentUserResolver.userId() } returns null

    val result = adapter.syncPlaylistData("p1")

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { spotifyPlaylist.getPlaylistTracksPage(any(), any(), any()) }
  }

  @Test
  fun `syncPlaylistData fetches and saves playlist tracks on first page`() {
    val page = buildTracksPage()
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylistTracksPage(accessToken, "p1", null) } returns page.right()
    every { playlistRepository.save(buildPlaylist("p1")) } just runs
    every { outboxPort.enqueue(any()) } just runs
    every { playlistRepository.updateLastSyncTime("p1", any()) } just runs

    val result = adapter.syncPlaylistData("p1")

    assertThat(result.isRight()).isTrue()
    verify { playlistRepository.save(buildPlaylist("p1")) }
  }

  @Test
  fun `syncPlaylistData delegates catalog sync to syncController`() {
    val page = buildTracksPage()
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylistTracksPage(accessToken, "p1", null) } returns page.right()
    every { playlistRepository.save(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs
    every { playlistRepository.updateLastSyncTime("p1", any()) } just runs

    adapter.syncPlaylistData("p1")

    verify {
      syncController.syncForTracks(
        listOf(CatalogSyncRequest("track-1", listOf("artist-1"), SyncCause.Playlist("p1", "track-1"))),
      )
    }
  }

  @Test
  fun `syncPlaylistData delegates found artists to catalogPort for assumption promotion`() {
    val page = buildTracksPage()
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylistTracksPage(accessToken, "p1", null) } returns page.right()
    every { playlistRepository.save(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs
    every { playlistRepository.updateLastSyncTime("p1", any()) } just runs

    adapter.syncPlaylistData("p1")

    verify { catalogPort.promoteAssumptionArtistsFoundOnPlaylist(setOf("artist-1")) }
  }

  @Test
  fun `syncPlaylistData updates lastSyncTime only on last page`() {
    val page = buildTracksPage()
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylistTracksPage(accessToken, "p1", null) } returns page.right()
    every { playlistRepository.save(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs
    every { playlistRepository.updateLastSyncTime("p1", any()) } just runs

    adapter.syncPlaylistData("p1")

    verify(exactly = 1) { playlistRepository.updateLastSyncTime(eq("p1"), any()) }
  }

  @Test
  fun `syncPlaylistData does not update lastSyncTime when there is a next page`() {
    val nextPageUrl = "https://api.spotify.com/v1/playlists/p1/tracks?offset=50&limit=50"
    val page = buildTracksPage(nextUrl = nextPageUrl)
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylistTracksPage(accessToken, "p1", null) } returns page.right()
    every { playlistRepository.save(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs

    adapter.syncPlaylistData("p1")

    verify(exactly = 0) { playlistRepository.updateLastSyncTime(any(), any()) }
  }

  @Test
  fun `syncPlaylistData enqueues next page event when nextUrl is present`() {
    val nextPageUrl = "https://api.spotify.com/v1/playlists/p1/tracks?offset=50&limit=50"
    val page = buildTracksPage(nextUrl = nextPageUrl)
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylistTracksPage(accessToken, "p1", null) } returns page.right()
    every { playlistRepository.save(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs

    adapter.syncPlaylistData("p1")

    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData("p1", nextPageUrl, "snap-1")) }
    verify(exactly = 0) { outboxPort.enqueue(any<DomainOutboxEvent.RunPlaylistChecks>()) }
  }

  @Test
  fun `syncPlaylistData enqueues RunPlaylistChecks only on last page`() {
    val page = buildTracksPage()
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylistTracksPage(accessToken, "p1", null) } returns page.right()
    every { playlistRepository.save(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs
    every { playlistRepository.updateLastSyncTime("p1", any()) } just runs

    adapter.syncPlaylistData("p1")

    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.RunPlaylistChecks("p1")) }
    verify(exactly = 0) { outboxPort.enqueue(any<DomainOutboxEvent.SyncPlaylistData>()) }
  }

  @Test
  fun `syncPlaylistData appends tracks on subsequent pages`() {
    val nextPageUrl = "https://api.spotify.com/v1/playlists/p1/tracks?offset=50&limit=50"
    val page = buildTracksPage()
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylistTracksPage(accessToken, "p1", nextPageUrl) } returns page.right()
    every { playlistRepository.appendTracks("p1", page.tracks) } just runs
    every { outboxPort.enqueue(any()) } just runs
    every { playlistRepository.updateLastSyncTime("p1", any()) } just runs

    val result = adapter.syncPlaylistData("p1", nextPageUrl)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { playlistRepository.appendTracks("p1", page.tracks) }
    verify(exactly = 0) { playlistRepository.save(any()) }
  }

  @Test
  fun `syncPlaylistData restarts from first page when snapshotId changes mid-paging`() {
    val nextPageUrl = "https://api.spotify.com/v1/playlists/p1/tracks?offset=50&limit=50"
    val page = buildTracksPage(snapshotId = "snap-2")  // snapshot changed
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylistTracksPage(accessToken, "p1", nextPageUrl) } returns page.right()
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.syncPlaylistData("p1", nextPageUrl, "snap-1")

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData("p1")) }
    verify(exactly = 0) { playlistRepository.appendTracks(any(), any()) }
    verify(exactly = 0) { playlistRepository.save(any()) }
  }

  @Test
  fun `syncPlaylistData proceeds normally when snapshotId matches on subsequent page`() {
    val nextPageUrl = "https://api.spotify.com/v1/playlists/p1/tracks?offset=50&limit=50"
    val page = buildTracksPage(snapshotId = "snap-1")
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylistTracksPage(accessToken, "p1", nextPageUrl) } returns page.right()
    every { playlistRepository.appendTracks("p1", page.tracks) } just runs
    every { outboxPort.enqueue(any()) } just runs
    every { playlistRepository.updateLastSyncTime("p1", any()) } just runs

    val result = adapter.syncPlaylistData("p1", nextPageUrl, "snap-1")

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { playlistRepository.appendTracks("p1", page.tracks) }
    verify(exactly = 0) { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData("p1")) }
  }


  @Test
  fun `syncPlaylistData returns Left when tracks fetch fails`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylistTracksPage(accessToken, "p1", null) } returns PlaylistSyncError.PLAYLIST_TRACKS_FETCH_FAILED.left()

    val result = adapter.syncPlaylistData("p1")

    assertThat(result.isLeft()).isTrue()
    verify(exactly = 0) { playlistRepository.save(any()) }
  }

  // --- updateSyncStatus enqueue tests ---

  @Test
  fun `updateSyncStatus enqueues SyncPlaylistData when activating playlist with no existing data`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.PASSIVE))
    every { playlistRepository.replaceAll(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistMetadata() } just runs

    val result = adapter.updateSyncStatus("p1", PlaylistSyncStatus.ACTIVE)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData("p1")) }
  }

  @Test
  fun `updateSyncStatus enqueues SyncPlaylistData when activating playlist with existing data`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.PASSIVE))
    every { playlistRepository.replaceAll(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistMetadata() } just runs

    val result = adapter.updateSyncStatus("p1", PlaylistSyncStatus.ACTIVE)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData("p1")) }
    verify(exactly = 0) { outboxPort.enqueue(any<DomainOutboxEvent.RunPlaylistChecks>()) }
  }

  @Test
  fun `updateSyncStatus notifies dashboard refresh after updating sync status`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.ACTIVE))
    every { playlistRepository.replaceAll(any()) } just runs
    every { playlistRepository.findByPlaylistId("p1") } returns mockk()
    every { dashboardRefresh.notifyUserPlaylistMetadata() } just runs
    every { playlistCheckRepository.deleteByPlaylistId("p1") } just runs
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.updateSyncStatus("p1", PlaylistSyncStatus.PASSIVE)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { dashboardRefresh.notifyUserPlaylistMetadata() }
  }

  @Test
  fun `updateSyncStatus notifies dashboard refresh when activating playlist`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.PASSIVE))
    every { playlistRepository.replaceAll(any()) } just runs
    every { playlistRepository.findByPlaylistId("p1") } returns mockk()
    every { outboxPort.enqueue(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistMetadata() } just runs

    val result = adapter.updateSyncStatus("p1", PlaylistSyncStatus.ACTIVE)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { dashboardRefresh.notifyUserPlaylistMetadata() }
  }

  @Test
  fun `updateSyncStatus deletes check documents when deactivating playlist`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.ACTIVE))
    every { playlistRepository.replaceAll(any()) } just runs
    every { playlistRepository.findByPlaylistId("p1") } returns mockk()
    every { dashboardRefresh.notifyUserPlaylistMetadata() } just runs
    every { playlistCheckRepository.deleteByPlaylistId("p1") } just runs
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.updateSyncStatus("p1", PlaylistSyncStatus.PASSIVE)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { playlistCheckRepository.deleteByPlaylistId("p1") }
  }

  @Test
  fun `updateSyncStatus does not delete check documents when activating playlist`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.PASSIVE))
    every { playlistRepository.replaceAll(any()) } just runs
    every { playlistRepository.findByPlaylistId("p1") } returns mockk()
    every { outboxPort.enqueue(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistMetadata() } just runs

    val result = adapter.updateSyncStatus("p1", PlaylistSyncStatus.ACTIVE)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { playlistCheckRepository.deleteByPlaylistId(any()) }
  }

  @Test
  fun `updateSyncStatus sets type ALL when activating playlist named All`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.PASSIVE, name = "All"))
    every { playlistRepository.replaceAll(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistMetadata() } just runs

    val result = adapter.updateSyncStatus("p1", PlaylistSyncStatus.ACTIVE)

    assertThat(result.isRight()).isTrue()
    val savedSlot = slot<List<PlaylistInfo>>()
    verify { playlistRepository.replaceAll(capture(savedSlot)) }
    assertThat(savedSlot.captured.find { it.spotifyPlaylistId == "p1" }!!.type).isEqualTo(PlaylistType.ALL)
  }

  @Test
  fun `updateSyncStatus sets type ALL when activating playlist named all case-insensitive`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.PASSIVE, name = "ALL"))
    every { playlistRepository.replaceAll(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistMetadata() } just runs

    val result = adapter.updateSyncStatus("p1", PlaylistSyncStatus.ACTIVE)

    assertThat(result.isRight()).isTrue()
    val savedSlot = slot<List<PlaylistInfo>>()
    verify { playlistRepository.replaceAll(capture(savedSlot)) }
    assertThat(savedSlot.captured.find { it.spotifyPlaylistId == "p1" }!!.type).isEqualTo(PlaylistType.ALL)
  }

  // --- enqueueSyncPlaylistData tests ---

  @Test
  fun `enqueueSyncPlaylistData returns Left when user not found`() {
    every { currentUserResolver.userId() } returns null

    val result = adapter.enqueueSyncPlaylistData("p1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(PlaylistSyncError.PLAYLIST_NOT_FOUND)
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `enqueueSyncPlaylistData returns Left when playlist not found`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns emptyList()

    val result = adapter.enqueueSyncPlaylistData("p1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(PlaylistSyncError.PLAYLIST_NOT_FOUND)
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `enqueueSyncPlaylistData returns Left when playlist is not active`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.PASSIVE))

    val result = adapter.enqueueSyncPlaylistData("p1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(PlaylistSyncError.PLAYLIST_SYNC_INACTIVE)
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `enqueueSyncPlaylistData enqueues SyncPlaylistData and returns Right`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1"))
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.enqueueSyncPlaylistData("p1")

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData("p1")) }
  }

  // --- Playlist Settings View read model tests (ADR-0014) ---

  @Test
  fun `getPlaylistSettingsView returns empty view when nothing has been built yet`() {
    every { playlistSettingsViewRepository.find() } returns null

    val result = adapter.getPlaylistSettingsView()

    assertThat(result.entries).isEmpty()
  }

  @Test
  fun `getPlaylistSettingsView returns the precomputed view`() {
    val view = PlaylistSettingsView(
      entries = listOf(PlaylistSettingsEntry(playlist = buildPlaylistInfo("p1"), numberOfTracks = 5, numberOfArtists = 3, numberOfMissingArtists = 1)),
    )
    every { playlistSettingsViewRepository.find() } returns view

    val result = adapter.getPlaylistSettingsView()

    assertThat(result).isEqualTo(view)
  }

  @Test
  fun `rebuildPlaylistSettingsView composes playlists, track counts and artist stats into one view`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1"), buildPlaylistInfo("p2"))
    every { playlistRepository.findTrackCounts() } returns mapOf("p1" to 10, "p2" to 20)
    every { playlistRepository.findDistinctArtistIds() } returns mapOf(
      "p1" to setOf(ArtistId("artist-1"), ArtistId("artist-2")),
      "p2" to setOf(ArtistId("artist-2"), ArtistId("artist-3")),
    )
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"), ArtistId("artist-2"), ArtistId("artist-3"))) } returns listOf(
      AppArtist(id = ArtistId("artist-1"), artistName = "Artist 1", lastSync = now),
    )
    val savedSlot = slot<PlaylistSettingsView>()
    every { playlistSettingsViewRepository.save(capture(savedSlot)) } just runs

    adapter.rebuildPlaylistSettingsView()

    val entries = savedSlot.captured.entries.associateBy { it.playlist.spotifyPlaylistId }
    assertThat(entries["p1"]!!.numberOfTracks).isEqualTo(10)
    assertThat(entries["p1"]!!.numberOfArtists).isEqualTo(2)
    assertThat(entries["p1"]!!.numberOfMissingArtists).isEqualTo(1)
    assertThat(entries["p2"]!!.numberOfTracks).isEqualTo(20)
    assertThat(entries["p2"]!!.numberOfArtists).isEqualTo(2)
    assertThat(entries["p2"]!!.numberOfMissingArtists).isEqualTo(2)
  }

  @Test
  fun `handle RebuildPlaylistSettingsView skips when no user exists`() {
    every { currentUserResolver.userId() } returns null

    val result = adapter.handle(DomainOutboxEvent.RebuildPlaylistSettingsView())

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { playlistSettingsViewRepository.save(any()) }
  }

  @Test
  fun `handle RebuildPlaylistSettingsView rebuilds the view for the current user`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns emptyList()
    every { playlistRepository.findTrackCounts() } returns emptyMap()
    every { playlistRepository.findDistinctArtistIds() } returns emptyMap()
    every { appArtistRepository.findByArtistIds(emptySet()) } returns emptyList()
    every { playlistSettingsViewRepository.save(any()) } just runs

    val result = adapter.handle(DomainOutboxEvent.RebuildPlaylistSettingsView())

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { playlistSettingsViewRepository.save(any()) }
  }

  @Test
  fun `syncPlaylists enqueues RebuildPlaylistSettingsView and RebuildDashboardReadModel`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylists(accessToken) } returns listOf(buildSpotifyItem("p1")).right()
    every { playlistRepository.findAll() } returns emptyList()
    every { playlistRepository.replaceAll(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistMetadata() } just runs
    every { outboxPort.enqueue(any()) } just runs

    adapter.syncPlaylists()

    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.RebuildPlaylistSettingsView()) }
    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.RebuildDashboardReadModel()) }
  }

  @Test
  fun `syncPlaylistData enqueues RebuildPlaylistSettingsView only once all pages are complete`() {
    val page = buildTracksPage()
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyPlaylist.getPlaylistTracksPage(accessToken, "p1", null) } returns page.right()
    every { playlistRepository.save(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs
    every { playlistRepository.updateLastSyncTime("p1", any()) } just runs

    adapter.syncPlaylistData("p1")

    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.RebuildPlaylistSettingsView()) }
  }

  @Test
  fun `updateSyncStatus enqueues RebuildPlaylistSettingsView and RebuildDashboardReadModel`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.ACTIVE))
    every { playlistRepository.replaceAll(any()) } just runs
    every { playlistRepository.findByPlaylistId("p1") } returns mockk()
    every { dashboardRefresh.notifyUserPlaylistMetadata() } just runs
    every { playlistCheckRepository.deleteByPlaylistId("p1") } just runs
    every { outboxPort.enqueue(any()) } just runs

    adapter.updateSyncStatus("p1", PlaylistSyncStatus.PASSIVE)

    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.RebuildPlaylistSettingsView()) }
    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.RebuildDashboardReadModel()) }
  }
}
