package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AppAlbum
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.playback.AppPlaybackItem
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.CatalogStats
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationRankEntry
import de.chrgroth.spotify.control.domain.model.playback.aggregation.PlaybackAggregation
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogBrowserPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackAggregationRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DashboardServiceTests {

  private val appPlaybackRepository: AppPlaybackRepositoryPort = mockk()
  private val appTrackRepository: AppTrackRepositoryPort = mockk()
  private val appArtistRepository: AppArtistRepositoryPort = mockk()
  private val appAlbumRepository: AppAlbumRepositoryPort = mockk()
  private val aggregationRepository: PlaybackAggregationRepositoryPort = mockk()
  private val catalogBrowser: CatalogBrowserPort = mockk()
  private val playlistRepository: PlaylistRepositoryPort = mockk()
  private val playlistCheckRepository: AppPlaylistCheckRepositoryPort = mockk()

  private val adapter = DashboardService(
    appPlaybackRepository, appTrackRepository, appArtistRepository, appAlbumRepository,
    aggregationRepository, catalogBrowser, playlistRepository, playlistCheckRepository,
    recentlyPlayedLimit = 5,
    topEntriesLimit = 3,
  )

  private val userId = UserId("user-1")
  private val syncTimestamp = Instant.fromEpochSeconds(0)
  private val someDate = LocalDate(2024, 1, 15)

  private val artist1 = AppArtist(
    id = ArtistId("artist-1"), artistName = "Artist One",
    lastSync = syncTimestamp,
  )

  private val track1 = AppTrack(
    id = TrackId("track-1"), title = "Track One",
    artistId = ArtistId("artist-1"), artistName = "Artist One", durationMs = 300_000L,
    albumId = AlbumId("album-1"), lastSync = syncTimestamp,
  )

  private fun emptyAgg(date: LocalDate = someDate) = PlaybackAggregation(
    userId = userId,
    type = AggregationPeriodType.DAY,
    periodStart = date,
    totalPlaybackSeconds = 0L,
    eventCount = 0L,
    distinctArtistCount = 0,
    distinctTrackCount = 0,
    distinctAlbumCount = 0,
    artistEntries = emptyList(),
    albumEntries = emptyList(),
    trackEntries = emptyList(),
    activityEntries = emptyList(),
  )

  private fun setupCommonMocks() {
    every { aggregationRepository.findByUserTypeAndPeriodRange(userId, AggregationPeriodType.DAY, any(), any()) } returns emptyList()
    every { aggregationRepository.sumEventCountByUser(userId) } returns 0L
    every { appPlaybackRepository.findRecentlyPlayed(userId, any()) } returns emptyList()
    every { playlistRepository.findByUserId(userId) } returns emptyList()
    every { playlistCheckRepository.countAll() } returns 0L
    every { playlistCheckRepository.countSucceeded() } returns 0L
    every { catalogBrowser.getCatalogStats() } returns CatalogStats(0, 0, 0)
    every { appTrackRepository.findByTrackIds(any()) } returns emptyList()
    every { appAlbumRepository.findByAlbumIds(any()) } returns emptyList()
    every { appArtistRepository.findByArtistIds(any()) } returns emptyList()
  }

  @Test
  fun `listening stats exclude items without tracked duration`() {
    setupCommonMocks()

    val agg = emptyAgg().copy(
      totalPlaybackSeconds = 180L,
      trackEntries = listOf(AggregationRankEntry(id = "track-1", name = "Track One", totalSeconds = 180L)),
      artistEntries = listOf(AggregationRankEntry(id = "artist-1", name = "Artist One", totalSeconds = 180L)),
    )
    every { aggregationRepository.findByUserTypeAndPeriodRange(userId, AggregationPeriodType.DAY, any(), any()) } returns listOf(agg)
    every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"))) } returns listOf(track1)
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns listOf(artist1)
    every { appAlbumRepository.findByAlbumIds(any()) } returns emptyList()

    val stats = adapter.getStats(userId)

    // 180 seconds = 3 minutes
    assertThat(stats.listeningStats.listenedMinutesLast30Days).isEqualTo(3L)
  }

  @Test
  fun `listening stats are zero when aggregations have no track entries`() {
    setupCommonMocks()

    val stats = adapter.getStats(userId)

    assertThat(stats.listeningStats.listenedMinutesLast30Days).isEqualTo(0L)
    assertThat(stats.listeningStats.topTracksLast30Days).isEmpty()
    assertThat(stats.listeningStats.topArtistsLast30Days).isEmpty()
    assertThat(stats.listeningStats.topAlbumsLast30Days).isEmpty()
  }

  @Test
  fun `listening stats aggregate play durations across multiple aggregation days`() {
    setupCommonMocks()

    val track2 = AppTrack(
      id = TrackId("track-2"), title = "Track Two",
      artistId = ArtistId("artist-1"), durationMs = 240_000L,
      lastSync = syncTimestamp,
    )
    val agg1 = emptyAgg(LocalDate(2024, 1, 14)).copy(
      totalPlaybackSeconds = 120L,
      trackEntries = listOf(AggregationRankEntry(id = "track-1", name = "Track One", totalSeconds = 120L)),
      artistEntries = listOf(AggregationRankEntry(id = "artist-1", name = "Artist One", totalSeconds = 120L)),
    )
    val agg2 = emptyAgg(LocalDate(2024, 1, 15)).copy(
      totalPlaybackSeconds = 180L,
      trackEntries = listOf(AggregationRankEntry(id = "track-2", name = "Track Two", totalSeconds = 180L)),
      artistEntries = listOf(AggregationRankEntry(id = "artist-1", name = "Artist One", totalSeconds = 180L)),
    )
    every { aggregationRepository.findByUserTypeAndPeriodRange(userId, AggregationPeriodType.DAY, any(), any()) } returns listOf(agg1, agg2)
    every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"), TrackId("track-2"))) } returns listOf(track1, track2)
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns listOf(artist1)
    every { appAlbumRepository.findByAlbumIds(any()) } returns emptyList()

    val stats = adapter.getStats(userId)

    // track-1: 120s, track-2: 180s → total 300s = 5 minutes
    assertThat(stats.listeningStats.listenedMinutesLast30Days).isEqualTo(5L)
    assertThat(stats.listeningStats.topTracksLast30Days).hasSize(2)
    assertThat(stats.listeningStats.topTracksLast30Days[0].artistName).isEqualTo("Artist One")
    assertThat(stats.listeningStats.topTracksLast30Days[0].trackDurationMs).isEqualTo(240_000L)
  }

  @Test
  fun `listening stats compute top albums by aggregating track play times per album`() {
    setupCommonMocks()

    val album1 = AppAlbum(id = AlbumId("album-1"), title = "Album One", artistName = "Artist One", lastSync = syncTimestamp)
    val album2 = AppAlbum(id = AlbumId("album-2"), title = "Album Two", artistName = "Artist One", lastSync = syncTimestamp)
    val track2 = AppTrack(
      id = TrackId("track-2"), title = "Track Two",
      artistId = ArtistId("artist-1"), durationMs = 120_000L,
      albumId = AlbumId("album-2"), lastSync = syncTimestamp,
    )
    val agg = emptyAgg().copy(
      totalPlaybackSeconds = 480L,
      trackEntries = listOf(
        AggregationRankEntry(id = "track-1", name = "Track One", totalSeconds = 300L),
        AggregationRankEntry(id = "track-2", name = "Track Two", totalSeconds = 180L),
      ),
      artistEntries = listOf(AggregationRankEntry(id = "artist-1", name = "Artist One", totalSeconds = 480L)),
    )
    every { aggregationRepository.findByUserTypeAndPeriodRange(userId, AggregationPeriodType.DAY, any(), any()) } returns listOf(agg)
    every { appTrackRepository.findByTrackIds(any()) } returns listOf(track1, track2)
    every { appArtistRepository.findByArtistIds(any()) } returns listOf(artist1)
    every { appAlbumRepository.findByAlbumIds(any()) } returns listOf(album1, album2)

    val stats = adapter.getStats(userId)

    assertThat(stats.listeningStats.topAlbumsLast30Days).hasSize(2)
    assertThat(stats.listeningStats.topAlbumsLast30Days[0].name).isEqualTo("Album One")
    assertThat(stats.listeningStats.topAlbumsLast30Days[0].totalMinutes).isEqualTo(5L)
    assertThat(stats.listeningStats.topAlbumsLast30Days[0].artistName).isEqualTo("Artist One")
    assertThat(stats.listeningStats.topAlbumsLast30Days[1].name).isEqualTo("Album Two")
    assertThat(stats.listeningStats.topAlbumsLast30Days[1].totalMinutes).isEqualTo(3L)
    assertThat(stats.listeningStats.topTracksLast30Days[0].albumName).isEqualTo("Album One")
    assertThat(stats.listeningStats.topTracksLast30Days[0].artistName).isEqualTo("Artist One")
  }

  @Test
  fun `recently played tracks include duration from secondsPlayed`() {
    every { aggregationRepository.findByUserTypeAndPeriodRange(userId, AggregationPeriodType.DAY, any(), any()) } returns emptyList()
    every { aggregationRepository.sumEventCountByUser(userId) } returns 1L
    every { playlistRepository.findByUserId(userId) } returns emptyList()
    every { playlistCheckRepository.countAll() } returns 0L
    every { playlistCheckRepository.countSucceeded() } returns 0L
    every { catalogBrowser.getCatalogStats() } returns CatalogStats(0, 0, 0)
    every { appAlbumRepository.findByAlbumIds(any()) } returns emptyList()
    every { appTrackRepository.findByTrackIds(any()) } returns emptyList()
    every { appArtistRepository.findByArtistIds(any()) } returns emptyList()

    val playbackItem = AppPlaybackItem(
      userId = userId,
      playedAt = Instant.fromEpochSeconds(1000),
      trackId = "track-1",
      secondsPlayed = 210L,
    )
    every { appPlaybackRepository.findRecentlyPlayed(userId, any()) } returns listOf(playbackItem)
    every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"))) } returns listOf(track1)
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns listOf(artist1)

    val stats = adapter.getStats(userId)

    assertThat(stats.recentlyPlayedTracks).hasSize(1)
    assertThat(stats.recentlyPlayedTracks[0].durationSeconds).isEqualTo(210L)
  }

  @Test
  fun `getPlaybackStats only queries aggregation repository`() {
    val agg = emptyAgg().copy(eventCount = 7L)
    every { aggregationRepository.findByUserTypeAndPeriodRange(userId, AggregationPeriodType.DAY, any(), any()) } returns listOf(agg)
    every { aggregationRepository.sumEventCountByUser(userId) } returns 42L

    val stats = adapter.getPlaybackStats(userId)

    assertThat(stats.totalPlaybackEvents).isEqualTo(42L)
    assertThat(stats.playbackEventsLast30Days).isEqualTo(7L)
    assertThat(stats.playbackEventsPerDay).hasSize(30)
    verify(exactly = 0) { playlistRepository.findByUserId(any()) }
    verify(exactly = 0) { playlistCheckRepository.countAll() }
    verify(exactly = 0) { catalogBrowser.getCatalogStats() }
    verify(exactly = 0) { appPlaybackRepository.findRecentlyPlayed(any(), any()) }
  }

  @Test
  fun `getPlaylistMetadata only queries playlist repository`() {
    every { playlistRepository.findByUserId(userId) } returns emptyList()

    val stats = adapter.getPlaylistMetadata(userId)

    assertThat(stats.syncedPlaylists).isEqualTo(0L)
    assertThat(stats.totalPlaylists).isEqualTo(0L)
    verify(exactly = 0) { aggregationRepository.findByUserTypeAndPeriodRange(any(), any(), any(), any()) }
    verify(exactly = 0) { aggregationRepository.sumEventCountByUser(any()) }
    verify(exactly = 0) { playlistCheckRepository.countAll() }
    verify(exactly = 0) { catalogBrowser.getCatalogStats() }
  }

  @Test
  fun `getRecentlyPlayed only queries playback and catalog repositories for track data`() {
    every { appPlaybackRepository.findRecentlyPlayed(userId, any()) } returns emptyList()
    every { appTrackRepository.findByTrackIds(any()) } returns emptyList()
    every { appAlbumRepository.findByAlbumIds(any()) } returns emptyList()
    every { appArtistRepository.findByArtistIds(any()) } returns emptyList()

    val stats = adapter.getRecentlyPlayed(userId)

    assertThat(stats.recentlyPlayedTracks).isEmpty()
    verify(exactly = 0) { aggregationRepository.findByUserTypeAndPeriodRange(any(), any(), any(), any()) }
    verify(exactly = 0) { aggregationRepository.sumEventCountByUser(any()) }
    verify(exactly = 0) { playlistRepository.findByUserId(any()) }
    verify(exactly = 0) { playlistCheckRepository.countAll() }
    verify(exactly = 0) { catalogBrowser.getCatalogStats() }
  }

  @Test
  fun `getListeningStats only queries aggregation and catalog repositories for stats`() {
    every { aggregationRepository.findByUserTypeAndPeriodRange(userId, AggregationPeriodType.DAY, any(), any()) } returns emptyList()
    every { appTrackRepository.findByTrackIds(any()) } returns emptyList()
    every { appAlbumRepository.findByAlbumIds(any()) } returns emptyList()
    every { appArtistRepository.findByArtistIds(any()) } returns emptyList()

    val stats = adapter.getListeningStats(userId)

    assertThat(stats.listeningStats.listenedMinutesLast30Days).isEqualTo(0L)
    verify(exactly = 0) { aggregationRepository.sumEventCountByUser(any()) }
    verify(exactly = 0) { appPlaybackRepository.findRecentlyPlayed(any(), any()) }
    verify(exactly = 0) { playlistRepository.findByUserId(any()) }
    verify(exactly = 0) { playlistCheckRepository.countAll() }
    verify(exactly = 0) { catalogBrowser.getCatalogStats() }
  }

  @Test
  fun `getPlaylistCheckStats only queries playlist check repository`() {
    every { playlistCheckRepository.countAll() } returns 3L
    every { playlistCheckRepository.countSucceeded() } returns 3L

    val stats = adapter.getPlaylistCheckStats()

    assertThat(stats.playlistCheckStats.totalChecks).isEqualTo(3L)
    assertThat(stats.playlistCheckStats.succeededChecks).isEqualTo(3L)
    assertThat(stats.playlistCheckStats.allSucceeded).isTrue()
    verify(exactly = 0) { aggregationRepository.findByUserTypeAndPeriodRange(any(), any(), any(), any()) }
    verify(exactly = 0) { aggregationRepository.sumEventCountByUser(any()) }
    verify(exactly = 0) { playlistRepository.findByUserId(any()) }
    verify(exactly = 0) { catalogBrowser.getCatalogStats() }
  }

  @Test
  fun `getCatalogStats only queries catalog browser`() {
    every { catalogBrowser.getCatalogStats() } returns CatalogStats(artistCount = 10L, albumCount = 20L, trackCount = 30L)

    val stats = adapter.getCatalogStats()

    assertThat(stats.catalogStats.artistCount).isEqualTo(10L)
    assertThat(stats.catalogStats.albumCount).isEqualTo(20L)
    assertThat(stats.catalogStats.trackCount).isEqualTo(30L)
    verify(exactly = 0) { aggregationRepository.findByUserTypeAndPeriodRange(any(), any(), any(), any()) }
    verify(exactly = 0) { aggregationRepository.sumEventCountByUser(any()) }
    verify(exactly = 0) { playlistRepository.findByUserId(any()) }
    verify(exactly = 0) { playlistCheckRepository.countAll() }
  }
}
