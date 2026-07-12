package de.chrgroth.spotify.control.domain.catalog

import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.SyncError
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AlbumSyncResult
import de.chrgroth.spotify.control.domain.model.catalog.AppAlbum
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.ArtistAlbumsPage
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistSyncStatus
import de.chrgroth.spotify.control.domain.model.catalog.SyncCause
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPartialPlayedItem
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.SyncTraceRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPartialPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.catalog.SpotifyCatalogPort
import de.chrgroth.spotify.control.domain.user.CurrentUserResolver
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CatalogServiceTests {

  private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
  private val spotifyCatalog: SpotifyCatalogPort = mockk()
  private val appArtistRepository: AppArtistRepositoryPort = mockk()
  private val appTrackRepository: AppTrackRepositoryPort = mockk()
  private val appAlbumRepository: AppAlbumRepositoryPort = mockk()
  private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort = mockk(relaxed = true)
  private val recentlyPartialPlayedRepository: RecentlyPartialPlayedRepositoryPort = mockk(relaxed = true)
  private val currentUserResolver: CurrentUserResolver = mockk()
  private val outboxPort: OutboxPort = mockk()
  private val playlistRepository: PlaylistRepositoryPort = mockk()
  private val playlistCheckRepository: AppPlaylistCheckRepositoryPort = mockk()
  private val dashboardRefresh: DashboardRefreshPort = mockk(relaxed = true)
  private val syncController: SyncController = mockk(relaxed = true)
  private val playbackAggregation: de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackAggregationPort = mockk(relaxed = true)
  private val syncTraceRepository: SyncTraceRepositoryPort = mockk(relaxed = true)

  private val adapter = CatalogService(
    spotifyAccessToken, spotifyCatalog,
    appArtistRepository, appTrackRepository, appAlbumRepository,
    recentlyPlayedRepository, recentlyPartialPlayedRepository,
    currentUserResolver, outboxPort,
    playlistRepository, playlistCheckRepository,
    dashboardRefresh, syncController,
    playbackAggregation, syncTraceRepository,
  )

  private val syncTimestamp = Instant.fromEpochSeconds(1)
  private val artist1 = AppArtist(
    id = ArtistId("artist-1"), artistName = "Artist One",
    lastSync = syncTimestamp,
  )
  private val artist2 = AppArtist(
    id = ArtistId("artist-2"), artistName = "Artist Two",
    lastSync = syncTimestamp,
  )
  private val track1 = AppTrack(id = TrackId("track-1"), title = "Track One", artistId = ArtistId("artist-1"), lastSync = syncTimestamp)
  private val track2 = AppTrack(id = TrackId("track-2"), title = "Track Two", artistId = ArtistId("artist-2"), lastSync = syncTimestamp)

  private val userId = UserId("user-1")
  private val accessToken = AccessToken("token")
  private val album1 = AppAlbum(id = AlbumId("album-1"), title = "Album One", artistId = ArtistId("artist-1"), artistName = "Artist One", lastSync = syncTimestamp)
  private val album2 = AppAlbum(id = AlbumId("album-2"), title = "Album Two", artistId = ArtistId("artist-2"), artistName = "Artist Two", lastSync = syncTimestamp)
  private val trackWithAlbum1 = AppTrack(
    id = TrackId("track-1"), title = "Track One",
    albumId = AlbumId("album-1"), artistId = ArtistId("artist-1"), lastSync = syncTimestamp,
  )
  private val trackWithAlbum2 = AppTrack(
    id = TrackId("track-2"), title = "Track Two",
    albumId = AlbumId("album-1"), artistId = ArtistId("artist-1"), lastSync = syncTimestamp,
  )
  private val trackWithAlbum3 = AppTrack(
    id = TrackId("track-3"), title = "Track Three",
    albumId = AlbumId("album-2"), artistId = ArtistId("artist-2"), lastSync = syncTimestamp,
  )
  private val albumSyncResult = AlbumSyncResult(album = album1, tracks = listOf(trackWithAlbum1, trackWithAlbum2))

  private fun recentlyPlayedItem(trackId: String, vararg artistIds: String) = RecentlyPlayedItem(
    trackId = TrackId(trackId),
    trackName = "Track $trackId",
    artistIds = artistIds.map { ArtistId(it) },
    artistNames = artistIds.map { "Artist $it" },
    playedAt = Instant.fromEpochSeconds(10),
  )

  private fun recentlyPartialPlayedItem(trackId: String, vararg artistIds: String) = RecentlyPartialPlayedItem(
    trackId = TrackId(trackId),
    trackName = "Track $trackId",
    artistIds = artistIds.map { ArtistId(it) },
    artistNames = artistIds.map { "Artist $it" },
    playedAt = Instant.fromEpochSeconds(20),
    startTime = Instant.fromEpochSeconds(15),
    playedSeconds = 42,
  )

  // --- setArtistSync / setArtistShallow tests ---

  @Test
  fun `setArtistSync returns error when artist not found`() {
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns emptyList()

    val result = adapter.setArtistSync("artist-1")

    assertThat(result.isLeft()).isTrue()
    verify(exactly = 0) { appArtistRepository.setSyncStatus(any(), any()) }
  }

  @Test
  fun `setArtistSync updates status, enqueues album sync and rebuilds aggregations`() {
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns listOf(artist1)
    every { appArtistRepository.setSyncStatus(ArtistId("artist-1"), ArtistSyncStatus.SYNC) } just runs
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.setArtistSync("artist-1")

    assertThat(result.isRight()).isTrue()
    verify { appArtistRepository.setSyncStatus(ArtistId("artist-1"), ArtistSyncStatus.SYNC) }
    verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums("artist-1")) }
    verify { playbackAggregation.rebuildAllAggregations() }
  }

  @Test
  fun `setArtistShallow returns error when artist not found`() {
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns emptyList()

    val result = adapter.setArtistShallow("artist-1")

    assertThat(result.isLeft()).isTrue()
    verify(exactly = 0) { appArtistRepository.setSyncStatus(any(), any()) }
  }

  @Test
  fun `setArtistShallow updates status, deletes albums and tracks and rebuilds aggregations`() {
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns listOf(artist1)
    every { appArtistRepository.setSyncStatus(ArtistId("artist-1"), ArtistSyncStatus.SHALLOW) } just runs
    every { appTrackRepository.deleteByArtistId(ArtistId("artist-1")) } just runs
    every { appAlbumRepository.deleteByArtistId(ArtistId("artist-1")) } just runs

    val result = adapter.setArtistShallow("artist-1")

    assertThat(result.isRight()).isTrue()
    verify { appArtistRepository.setSyncStatus(ArtistId("artist-1"), ArtistSyncStatus.SHALLOW) }
    verify { appTrackRepository.deleteByArtistId(ArtistId("artist-1")) }
    verify { appAlbumRepository.deleteByArtistId(ArtistId("artist-1")) }
    verify { playbackAggregation.rebuildAllAggregations() }
  }

  // --- resyncArtist tests ---

  @Test
  fun `resyncArtist returns error when artist not found`() {
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns emptyList()

    val result = adapter.resyncArtist("artist-1")

    assertThat(result.isLeft()).isTrue()
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `resyncArtist enqueues SyncArtistAlbums for existing artist`() {
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns listOf(artist1)
    every { currentUserResolver.userId() } returns userId
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.resyncArtist("artist-1")

    assertThat(result.isRight()).isTrue()
    verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums("artist-1")) }
    verify(exactly = 0) { outboxPort.enqueue(match { it is DomainOutboxEvent.SyncAlbumDetails }) }
  }

  @Test
  fun `resyncArtist does nothing when no users available`() {
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns listOf(artist1)
    every { currentUserResolver.userId() } returns null

    val result = adapter.resyncArtist("artist-1")

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  // --- resyncCatalog tests ---

  @Test
  fun `resyncCatalog enqueues playback-based sync when catalog is empty`() {
    every { appArtistRepository.findAll() } returns emptyList()
    every { currentUserResolver.userId() } returns userId
    every { recentlyPlayedRepository.findSince(null) } returns listOf(recentlyPlayedItem("track-1", "artist-1", "artist-1b"))
    every { recentlyPartialPlayedRepository.findSince(null) } returns listOf(recentlyPartialPlayedItem("track-2", "artist-2"))

    val result = adapter.resyncCatalog()

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { outboxPort.enqueue(any()) }
    verify {
      syncController.syncForTracks(
        match { requests ->
          requests.size == 2 &&
            requests.any { it == CatalogSyncRequest("track-1", listOf("artist-1"), SyncCause.ManualResync) } &&
            requests.any { it == CatalogSyncRequest("track-2", listOf("artist-2"), SyncCause.ManualResync) }
        },
      )
    }
  }

  @Test
  fun `resyncCatalog enqueues SyncArtistAlbums for all artists`() {
    every { appArtistRepository.findAll() } returns listOf(artist1, artist2)
    every { currentUserResolver.userId() } returns userId
    every { outboxPort.enqueue(any()) } just runs
    every { recentlyPlayedRepository.findSince(null) } returns emptyList()
    every { recentlyPartialPlayedRepository.findSince(null) } returns emptyList()

    val result = adapter.resyncCatalog()

    assertThat(result.isRight()).isTrue()
    verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums("artist-1")) }
    verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums("artist-2")) }
  }

  @Test
  fun `resyncCatalog does not enqueue SyncArtistAlbums for shallow artists`() {
    val shallowArtist = artist2.copy(syncStatus = ArtistSyncStatus.SHALLOW)
    every { appArtistRepository.findAll() } returns listOf(artist1, shallowArtist)
    every { currentUserResolver.userId() } returns userId
    every { outboxPort.enqueue(any()) } just runs
    every { recentlyPlayedRepository.findSince(null) } returns emptyList()
    every { recentlyPartialPlayedRepository.findSince(null) } returns emptyList()

    val result = adapter.resyncCatalog()

    assertThat(result.isRight()).isTrue()
    verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums("artist-1")) }
    verify(exactly = 0) { outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums("artist-2")) }
  }

  @Test
  fun `resyncCatalog does not enqueue events when no users available`() {
    every { appArtistRepository.findAll() } returns listOf(artist1)
    every { currentUserResolver.userId() } returns null

    val result = adapter.resyncCatalog()

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  // --- handle(ResyncCatalog) tests ---

  @Test
  fun `handle ResyncCatalog returns success`() {
    every { appArtistRepository.findAll() } returns listOf(artist1)
    every { currentUserResolver.userId() } returns userId
    every { outboxPort.enqueue(any()) } just runs
    every { recentlyPlayedRepository.findSince(null) } returns emptyList()
    every { recentlyPartialPlayedRepository.findSince(null) } returns emptyList()

    val result = adapter.handle(DomainOutboxEvent.ResyncCatalog())

    assertThat(result.isRight()).isTrue()
  }

  @Test
  fun `handle ResyncCatalog returns success when catalog is empty`() {
    every { appArtistRepository.findAll() } returns emptyList()
    every { currentUserResolver.userId() } returns null

    val result = adapter.handle(DomainOutboxEvent.ResyncCatalog())

    assertThat(result.isRight()).isTrue()
  }

  // --- SyncAlbumDetails tests ---

  @Test
  fun `handle SyncAlbumDetails returns success when no users available`() {
    every { currentUserResolver.userId() } returns null

    val result = adapter.handle(DomainOutboxEvent.SyncAlbumDetails("album-1"))

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { spotifyCatalog.getAlbum(any(), any()) }
  }

  @Test
  fun `handle SyncAlbumDetails fetches only tracks and skips album upsert when album metadata already known`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { appAlbumRepository.findByAlbumIds(setOf(AlbumId("album-1"))) } returns listOf(album1)
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns emptyList()
    every { spotifyCatalog.getAlbumTracks(accessToken, album1) } returns listOf(trackWithAlbum1, trackWithAlbum2).right()
    every { appTrackRepository.upsertAll(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.handle(DomainOutboxEvent.SyncAlbumDetails("album-1"))

    assertThat(result.isRight()).isTrue()
    verify { spotifyCatalog.getAlbumTracks(accessToken, album1) }
    verify(exactly = 0) { spotifyCatalog.getAlbum(any(), any()) }
    verify { appTrackRepository.upsertAll(listOf(trackWithAlbum1, trackWithAlbum2)) }
    verify(exactly = 0) { appAlbumRepository.upsertAll(any()) }
  }

  @Test
  fun `handle SyncAlbumDetails falls back to full album fetch and upserts album when metadata not yet known`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { appAlbumRepository.findByAlbumIds(setOf(AlbumId("album-1"))) } returns emptyList()
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns emptyList()
    every { spotifyCatalog.getAlbum(accessToken, "album-1") } returns albumSyncResult.right()
    every { appTrackRepository.upsertAll(any()) } just runs
    every { appAlbumRepository.upsertAll(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.handle(DomainOutboxEvent.SyncAlbumDetails("album-1"))

    assertThat(result.isRight()).isTrue()
    verify { spotifyCatalog.getAlbum(accessToken, "album-1") }
    verify(exactly = 0) { spotifyCatalog.getAlbumTracks(any(), any()) }
    verify { appTrackRepository.upsertAll(listOf(trackWithAlbum1, trackWithAlbum2)) }
    verify { appAlbumRepository.upsertAll(listOf(album1)) }
  }

  @Test
  fun `handle SyncAlbumDetails does not sync artists found in album tracks`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { appAlbumRepository.findByAlbumIds(setOf(AlbumId("album-1"))) } returns emptyList()
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns emptyList()
    every { spotifyCatalog.getAlbum(accessToken, "album-1") } returns albumSyncResult.right()
    every { appTrackRepository.upsertAll(any()) } just runs
    every { appAlbumRepository.upsertAll(any()) } just runs

    adapter.handle(DomainOutboxEvent.SyncAlbumDetails("album-1"))

    verify(exactly = 0) { syncController.syncArtists(any(), any()) }
  }

  @Test
  fun `handle SyncAlbumDetails returns failed when album endpoint returns error`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { appAlbumRepository.findByAlbumIds(setOf(AlbumId("album-1"))) } returns emptyList()
    every { spotifyCatalog.getAlbum(accessToken, "album-1") } returns SyncError.TRACK_DETAILS_FETCH_FAILED.left()

    val result = adapter.handle(DomainOutboxEvent.SyncAlbumDetails("album-1"))

    assertThat(result.isLeft()).isTrue()
    verify(exactly = 0) { appTrackRepository.upsertAll(any()) }
  }

  @Test
  fun `handle SyncAlbumDetails returns rate limited when endpoint returns rate limit error`() {
    val rateLimitError = SpotifyRateLimitError(30.seconds)
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { appAlbumRepository.findByAlbumIds(setOf(AlbumId("album-1"))) } returns emptyList()
    every { spotifyCatalog.getAlbum(accessToken, "album-1") } returns rateLimitError.left()

    val result = adapter.handle(DomainOutboxEvent.SyncAlbumDetails("album-1"))

    assertThat(result.isLeft()).isTrue()
  }

  @Test
  fun `handle SyncAlbumDetails skips sync when known album's artist is shallow`() {
    every { currentUserResolver.userId() } returns userId
    every { appAlbumRepository.findByAlbumIds(setOf(AlbumId("album-1"))) } returns listOf(album1)
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns listOf(artist1.copy(syncStatus = ArtistSyncStatus.SHALLOW))

    val result = adapter.handle(DomainOutboxEvent.SyncAlbumDetails("album-1"))

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { spotifyCatalog.getAlbumTracks(any(), any()) }
    verify(exactly = 0) { appTrackRepository.upsertAll(any()) }
  }

  @Test
  fun `handle SyncAlbumDetails discards result when fetched album's artist is shallow`() {
    every { currentUserResolver.userId() } returns userId
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { appAlbumRepository.findByAlbumIds(setOf(AlbumId("album-1"))) } returns emptyList()
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns listOf(artist1.copy(syncStatus = ArtistSyncStatus.SHALLOW))
    every { spotifyCatalog.getAlbum(accessToken, "album-1") } returns albumSyncResult.right()

    val result = adapter.handle(DomainOutboxEvent.SyncAlbumDetails("album-1"))

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { appTrackRepository.upsertAll(any()) }
    verify(exactly = 0) { appAlbumRepository.upsertAll(any()) }
  }

  // --- handle(SyncArtistAlbums) tests ---

  @Test
  fun `handle SyncArtistAlbums enqueues SyncAlbumDetails for new albums and completes when no next page`() {
    val page = ArtistAlbumsPage(albums = listOf(album1, album2), nextUrl = null)
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns emptyList()
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyCatalog.getArtistAlbumsPage(accessToken, "artist-1", null) } returns page.right()
    every { appAlbumRepository.findByAlbumIds(setOf(AlbumId("album-1"), AlbumId("album-2"))) } returns emptyList()
    every { appAlbumRepository.upsertAll(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.handle(DomainOutboxEvent.SyncArtistAlbums("artist-1"))

    assertThat(result.isRight()).isTrue()
    verify { appAlbumRepository.upsertAll(listOf(album1, album2)) }
    verify { outboxPort.enqueue(DomainOutboxEvent.SyncAlbumDetails("album-1")) }
    verify { outboxPort.enqueue(DomainOutboxEvent.SyncAlbumDetails("album-2")) }
    verify(exactly = 0) { outboxPort.enqueue(match { it is DomainOutboxEvent.SyncArtistAlbums }) }
  }

  @Test
  fun `handle SyncArtistAlbums enqueues next page via outbox when nextUrl is present`() {
    val nextPageUrl = "https://api.spotify.com/v1/artists/artist-1/albums?offset=50&limit=50"
    val page = ArtistAlbumsPage(albums = listOf(album1), nextUrl = nextPageUrl)
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns emptyList()
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyCatalog.getArtistAlbumsPage(accessToken, "artist-1", null) } returns page.right()
    every { appAlbumRepository.findByAlbumIds(setOf(AlbumId("album-1"))) } returns emptyList()
    every { appAlbumRepository.upsertAll(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.handle(DomainOutboxEvent.SyncArtistAlbums("artist-1"))

    assertThat(result.isRight()).isTrue()
    verify { outboxPort.enqueue(DomainOutboxEvent.SyncAlbumDetails("album-1")) }
    verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums("artist-1", nextPageUrl)) }
  }

  @Test
  fun `handle SyncArtistAlbums processes subsequent page and fetches using nextUrl`() {
    val nextPageUrl = "https://api.spotify.com/v1/artists/artist-1/albums?offset=50&limit=50"
    val page = ArtistAlbumsPage(albums = listOf(album2), nextUrl = null)
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns emptyList()
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyCatalog.getArtistAlbumsPage(accessToken, "artist-1", nextPageUrl) } returns page.right()
    every { appAlbumRepository.findByAlbumIds(setOf(AlbumId("album-2"))) } returns emptyList()
    every { appAlbumRepository.upsertAll(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.handle(DomainOutboxEvent.SyncArtistAlbums("artist-1", nextPageUrl))

    assertThat(result.isRight()).isTrue()
    verify { outboxPort.enqueue(DomainOutboxEvent.SyncAlbumDetails("album-2")) }
    verify(exactly = 0) { outboxPort.enqueue(match { it is DomainOutboxEvent.SyncArtistAlbums }) }
  }

  @Test
  fun `handle SyncArtistAlbums enqueues only new albums and skips existing ones`() {
    val page = ArtistAlbumsPage(albums = listOf(album1, album2), nextUrl = null)
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns emptyList()
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyCatalog.getArtistAlbumsPage(accessToken, "artist-1", null) } returns page.right()
    every { appAlbumRepository.findByAlbumIds(setOf(AlbumId("album-1"), AlbumId("album-2"))) } returns listOf(album1)
    every { appAlbumRepository.upsertAll(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.handle(DomainOutboxEvent.SyncArtistAlbums("artist-1"))

    assertThat(result.isRight()).isTrue()
    verify { appAlbumRepository.upsertAll(listOf(album2)) }
    verify { outboxPort.enqueue(DomainOutboxEvent.SyncAlbumDetails("album-2")) }
    verify(exactly = 0) { outboxPort.enqueue(DomainOutboxEvent.SyncAlbumDetails("album-1")) }
  }

  @Test
  fun `handle SyncArtistAlbums skips next page when all albums on current page are already in catalog`() {
    val nextPageUrl = "https://api.spotify.com/v1/artists/artist-1/albums?offset=50&limit=50"
    val page = ArtistAlbumsPage(albums = listOf(album1, album2), nextUrl = nextPageUrl)
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns emptyList()
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyCatalog.getArtistAlbumsPage(accessToken, "artist-1", null) } returns page.right()
    every { appAlbumRepository.findByAlbumIds(setOf(AlbumId("album-1"), AlbumId("album-2"))) } returns listOf(album1, album2)
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.handle(DomainOutboxEvent.SyncArtistAlbums("artist-1"))

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { outboxPort.enqueue(any()) }
    verify(exactly = 0) { appAlbumRepository.upsertAll(any()) }
  }

  @Test
  fun `handle SyncArtistAlbums enqueues next page when some albums on current page are new`() {
    val nextPageUrl = "https://api.spotify.com/v1/artists/artist-1/albums?offset=50&limit=50"
    val page = ArtistAlbumsPage(albums = listOf(album1, album2), nextUrl = nextPageUrl)
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns emptyList()
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyCatalog.getArtistAlbumsPage(accessToken, "artist-1", null) } returns page.right()
    every { appAlbumRepository.findByAlbumIds(setOf(AlbumId("album-1"), AlbumId("album-2"))) } returns listOf(album1)
    every { appAlbumRepository.upsertAll(any()) } just runs
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.handle(DomainOutboxEvent.SyncArtistAlbums("artist-1"))

    assertThat(result.isRight()).isTrue()
    verify { appAlbumRepository.upsertAll(listOf(album2)) }
    verify { outboxPort.enqueue(DomainOutboxEvent.SyncAlbumDetails("album-2")) }
    verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums("artist-1", nextPageUrl)) }
  }

  @Test
  fun `handle SyncArtistAlbums returns error when artist albums page fetch fails`() {
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns emptyList()
    every { spotifyAccessToken.getValidAccessToken() } returns accessToken
    every { spotifyCatalog.getArtistAlbumsPage(accessToken, "artist-1", null) } returns SyncError.ARTIST_DETAILS_FETCH_FAILED.left()

    val result = adapter.handle(DomainOutboxEvent.SyncArtistAlbums("artist-1"))

    assertThat(result.isLeft()).isTrue()
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `handle SyncArtistAlbums skips sync when artist is shallow`() {
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns listOf(artist1.copy(syncStatus = ArtistSyncStatus.SHALLOW))

    val result = adapter.handle(DomainOutboxEvent.SyncArtistAlbums("artist-1"))

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { spotifyCatalog.getArtistAlbumsPage(any(), any(), any()) }
  }

  // --- wipeCatalog tests ---

  @Test
  fun `wipeCatalog deletes all catalog data, deactivates playlists and deletes checks`() {
    every { appArtistRepository.deleteAll() } just runs
    every { appAlbumRepository.deleteAll() } just runs
    every { appTrackRepository.deleteAll() } just runs
    every { playlistRepository.setAllSyncInactive() } just runs
    every { playlistCheckRepository.deleteAll() } just runs

    val result = adapter.wipeCatalog()

    assertThat(result.isRight()).isTrue()
    verify { appArtistRepository.deleteAll() }
    verify { appAlbumRepository.deleteAll() }
    verify { appTrackRepository.deleteAll() }
    verify { playlistRepository.setAllSyncInactive() }
    verify { playlistCheckRepository.deleteAll() }
  }

  // --- enqueueArtistAlbumsSync tests ---

  @Test
  fun `enqueueArtistAlbumsSync only enqueues syncable artists`() {
    val shallowArtist = artist2.copy(syncStatus = ArtistSyncStatus.SHALLOW)
    every { appArtistRepository.findAll() } returns listOf(artist1, shallowArtist)
    every { currentUserResolver.userId() } returns userId
    every { outboxPort.enqueue(any()) } just runs

    adapter.enqueueArtistAlbumsSync(partition = 0, totalPartitions = 1)

    verify { outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums("artist-1")) }
    verify(exactly = 0) { outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums("artist-2")) }
  }

  @Test
  fun `enqueueArtistAlbumsSync does nothing when no users available`() {
    every { appArtistRepository.findAll() } returns listOf(artist1)
    every { currentUserResolver.userId() } returns null

    adapter.enqueueArtistAlbumsSync(partition = 0, totalPartitions = 1)

    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `enqueuePlaybackArtistsForSync delegates playback tracks to syncController using only primary artist per track`() {
    every { currentUserResolver.userId() } returns userId
    every { recentlyPlayedRepository.findSince(null) } returns listOf(recentlyPlayedItem("track-1", "artist-1", "artist-1b"))
    every { recentlyPartialPlayedRepository.findSince(null) } returns listOf(recentlyPartialPlayedItem("track-2", "artist-2"))

    adapter.enqueuePlaybackArtistsForSync()

    verify {
      syncController.syncForTracks(
        match { requests ->
          requests.size == 2 &&
            requests.any { it == CatalogSyncRequest("track-1", listOf("artist-1"), SyncCause.ManualResync) } &&
            requests.any { it == CatalogSyncRequest("track-2", listOf("artist-2"), SyncCause.ManualResync) }
        },
      )
    }
  }
}
