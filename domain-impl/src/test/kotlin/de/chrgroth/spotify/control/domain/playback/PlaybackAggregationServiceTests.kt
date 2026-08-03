package de.chrgroth.spotify.control.domain.playback

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AppAlbum
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistSyncStatus
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.AppPlaybackItem
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationRankEntry
import de.chrgroth.spotify.control.domain.model.playback.aggregation.PlaybackAggregation
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.playback.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackAggregationRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackDigestNotificationPort
import de.chrgroth.spotify.control.domain.user.CurrentUserResolver
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlaybackAggregationServiceTests {

  private val currentUserResolver: CurrentUserResolver = mockk()
  private val appPlaybackRepository: AppPlaybackRepositoryPort = mockk()
  private val appTrackRepository: AppTrackRepositoryPort = mockk()
  private val appArtistRepository: AppArtistRepositoryPort = mockk()
  private val appAlbumRepository: AppAlbumRepositoryPort = mockk(relaxed = true)
  private val aggregationRepository: PlaybackAggregationRepositoryPort = mockk(relaxed = true)
  private val outboxPort: OutboxPort = mockk(relaxed = true)
  private val meterRegistry = SimpleMeterRegistry()
  private val digestNotification: PlaybackDigestNotificationPort = mockk(relaxed = true)

  private val service = PlaybackAggregationService(
    currentUserResolver = currentUserResolver,
    appPlaybackRepository = appPlaybackRepository,
    appTrackRepository = appTrackRepository,
    appArtistRepository = appArtistRepository,
    appAlbumRepository = appAlbumRepository,
    aggregationRepository = aggregationRepository,
    outboxPort = outboxPort,
    meterRegistry = meterRegistry,
    digestNotification = digestNotification,
  )

  private val userId = UserId("user-1")
  private val date = LocalDate(2024, 1, 15)
  private val syncTimestamp = Instant.fromEpochSeconds(0)

  @Test
  fun `aggregate day persists album entries and distinct album count`() {
    every { currentUserResolver.userId() } returns userId
    val savedAggregation = slot<PlaybackAggregation>()
    every { aggregationRepository.save(capture(savedAggregation)) } returns Unit
    every { appPlaybackRepository.findAllBetween(any(), any()) } returns listOf(
      AppPlaybackItem(Instant.fromEpochSeconds(1), "track-1", 120L),
      AppPlaybackItem(Instant.fromEpochSeconds(2), "track-2", 180L),
      AppPlaybackItem(Instant.fromEpochSeconds(3), "track-3", 60L),
    )
    every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"), TrackId("track-2"), TrackId("track-3"))) } returns listOf(
      AppTrack(
        id = TrackId("track-1"),
        title = "Track One",
        albumId = AlbumId("album-1"),
        albumName = "Album One",
        artistId = ArtistId("artist-1"),
        lastSync = syncTimestamp,
      ),
      AppTrack(
        id = TrackId("track-2"),
        title = "Track Two",
        albumId = AlbumId("album-1"),
        albumName = "Album One",
        artistId = ArtistId("artist-2"),
        lastSync = syncTimestamp,
      ),
      AppTrack(
        id = TrackId("track-3"),
        title = "Track Three",
        albumName = "Loose Single",
        artistId = ArtistId("artist-2"),
        lastSync = syncTimestamp,
      ),
    )
    every { appArtistRepository.findByArtistIds(any()) } returns listOf(
      AppArtist(id = ArtistId("artist-1"), artistName = "Artist One", lastSync = syncTimestamp),
      AppArtist(id = ArtistId("artist-2"), artistName = "Artist Two", lastSync = syncTimestamp),
    )

    val result = service.handle(DomainOutboxEvent.AggregatePlaybackData(AggregationPeriodType.DAY, date))

    assertThat(result.isRight()).isTrue()
    assertThat(savedAggregation.captured.distinctAlbumCount).isEqualTo(2)
    assertThat(savedAggregation.captured.albumEntries).containsExactly(
      AggregationRankEntry(id = "album-1", name = "Album One", totalSeconds = 300L),
      AggregationRankEntry(id = "fallback:Loose Single", name = "Loose Single", totalSeconds = 60L),
    )
  }

  @Test
  fun `aggregate day enriches track, artist and album entries with images and names`() {
    every { currentUserResolver.userId() } returns userId
    val savedAggregation = slot<PlaybackAggregation>()
    every { aggregationRepository.save(capture(savedAggregation)) } returns Unit
    every { appPlaybackRepository.findAllBetween(any(), any()) } returns listOf(
      AppPlaybackItem(Instant.fromEpochSeconds(1), "track-1", 120L),
    )
    every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"))) } returns listOf(
      AppTrack(
        id = TrackId("track-1"),
        title = "Track One",
        albumId = AlbumId("album-1"),
        albumName = "Album One",
        artistId = ArtistId("artist-1"),
        durationMs = 210_000L,
        lastSync = syncTimestamp,
      ),
    )
    every { appArtistRepository.findByArtistIds(setOf(ArtistId("artist-1"))) } returns listOf(
      AppArtist(id = ArtistId("artist-1"), artistName = "Artist One", imageLink = "https://example.org/artist-1.jpg", lastSync = syncTimestamp),
    )
    every { appAlbumRepository.findByAlbumIds(setOf(AlbumId("album-1"))) } returns listOf(
      AppAlbum(id = AlbumId("album-1"), title = "Album One", imageLink = "https://example.org/album-1.jpg", artistName = "Artist One", lastSync = syncTimestamp),
    )

    val result = service.handle(DomainOutboxEvent.AggregatePlaybackData(AggregationPeriodType.DAY, date))

    assertThat(result.isRight()).isTrue()
    assertThat(savedAggregation.captured.trackEntries).containsExactly(
      AggregationRankEntry(
        id = "track-1",
        name = "Track One",
        totalSeconds = 120L,
        imageLink = "https://example.org/album-1.jpg",
        artistName = "Artist One",
        albumName = "Album One",
        trackDurationMs = 210_000L,
      ),
    )
    assertThat(savedAggregation.captured.artistEntries).containsExactly(
      AggregationRankEntry(id = "artist-1", name = "Artist One", totalSeconds = 120L, imageLink = "https://example.org/artist-1.jpg"),
    )
    assertThat(savedAggregation.captured.albumEntries).containsExactly(
      AggregationRankEntry(
        id = "album-1",
        name = "Album One",
        totalSeconds = 120L,
        imageLink = "https://example.org/album-1.jpg",
        artistName = "Artist One",
      ),
    )
  }

  @Test
  fun `aggregate week merge carries forward enrichment fields from the first daily entry`() {
    every { currentUserResolver.userId() } returns userId
    val weekStart = LocalDate(2024, 1, 15)
    val savedAggregation = slot<PlaybackAggregation>()
    every { aggregationRepository.save(capture(savedAggregation)) } returns Unit
    every {
      aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.DAY, weekStart, LocalDate(2024, 1, 21))
    } returns listOf(
      dayAggregation(
        weekStart,
        trackEntries = listOf(
          AggregationRankEntry(
            id = "track-1",
            name = "Track One",
            totalSeconds = 120L,
            imageLink = "https://example.org/album-1.jpg",
            artistName = "Artist One",
            albumName = "Album One",
            trackDurationMs = 210_000L,
          ),
        ),
      ),
    )

    val result = service.handle(DomainOutboxEvent.AggregatePlaybackData(AggregationPeriodType.WEEK, weekStart))

    assertThat(result.isRight()).isTrue()
    assertThat(savedAggregation.captured.trackEntries).containsExactly(
      AggregationRankEntry(
        id = "track-1",
        name = "Track One",
        totalSeconds = 120L,
        imageLink = "https://example.org/album-1.jpg",
        artistName = "Artist One",
        albumName = "Album One",
        trackDurationMs = 210_000L,
      ),
    )
  }

  @Test
  fun `findByPeriods delegates to aggregation repository`() {
    val periods = listOf(AggregationPeriodType.DAY to date)
    every { aggregationRepository.findByPeriods(periods, 5) } returns emptyMap()

    val result = service.findByPeriods(periods, 5)

    assertThat(result).isEmpty()
    verify(exactly = 1) { aggregationRepository.findByPeriods(periods, 5) }
  }

  @Test
  fun `aggregate day excludes playback from shallow artists`() {
    every { currentUserResolver.userId() } returns userId
    val savedAggregation = slot<PlaybackAggregation>()
    every { aggregationRepository.save(capture(savedAggregation)) } returns Unit
    every { appPlaybackRepository.findAllBetween(any(), any()) } returns listOf(
      AppPlaybackItem(Instant.fromEpochSeconds(1), "track-1", 120L),
      AppPlaybackItem(Instant.fromEpochSeconds(2), "track-2", 180L),
    )
    every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"), TrackId("track-2"))) } returns listOf(
      AppTrack(id = TrackId("track-1"), title = "Track One", artistId = ArtistId("artist-1"), lastSync = syncTimestamp),
      AppTrack(id = TrackId("track-2"), title = "Track Two", artistId = ArtistId("artist-2"), lastSync = syncTimestamp),
    )
    every { appArtistRepository.findByArtistIds(any()) } returns listOf(
      AppArtist(id = ArtistId("artist-1"), artistName = "Artist One", lastSync = syncTimestamp, syncStatus = ArtistSyncStatus.SYNC),
      AppArtist(id = ArtistId("artist-2"), artistName = "Artist Two", lastSync = syncTimestamp, syncStatus = ArtistSyncStatus.SHALLOW),
    )

    val result = service.handle(DomainOutboxEvent.AggregatePlaybackData(AggregationPeriodType.DAY, date))

    assertThat(result.isRight()).isTrue()
    assertThat(savedAggregation.captured.totalPlaybackSeconds).isEqualTo(120L)
    assertThat(savedAggregation.captured.artistEntries.map { it.id }).containsExactly("artist-1")
  }

  @Test
  fun `aggregate day excludes playback with unresolvable track instead of falling back to unknown artist`() {
    every { currentUserResolver.userId() } returns userId
    val savedAggregation = slot<PlaybackAggregation>()
    every { aggregationRepository.save(capture(savedAggregation)) } returns Unit
    every { appPlaybackRepository.findAllBetween(any(), any()) } returns listOf(
      AppPlaybackItem(Instant.fromEpochSeconds(1), "track-1", 120L),
      AppPlaybackItem(Instant.fromEpochSeconds(2), "track-2", 180L),
    )
    every { appTrackRepository.findByTrackIds(setOf(TrackId("track-1"), TrackId("track-2"))) } returns listOf(
      AppTrack(id = TrackId("track-1"), title = "Track One", artistId = ArtistId("artist-1"), lastSync = syncTimestamp),
    )
    every { appArtistRepository.findByArtistIds(any()) } returns listOf(
      AppArtist(id = ArtistId("artist-1"), artistName = "Artist One", lastSync = syncTimestamp, syncStatus = ArtistSyncStatus.SYNC),
    )

    val result = service.handle(DomainOutboxEvent.AggregatePlaybackData(AggregationPeriodType.DAY, date))

    assertThat(result.isRight()).isTrue()
    assertThat(savedAggregation.captured.totalPlaybackSeconds).isEqualTo(120L)
    assertThat(savedAggregation.captured.artistEntries.map { it.id }).containsExactly("artist-1")
  }

  @Test
  fun `aggregate week merges album entries from day aggregations`() {
    every { currentUserResolver.userId() } returns userId
    val weekStart = LocalDate(2024, 1, 15)
    val day1 = LocalDate(2024, 1, 15)
    val day2 = LocalDate(2024, 1, 16)
    val savedAggregation = slot<PlaybackAggregation>()
    every { aggregationRepository.save(capture(savedAggregation)) } returns Unit
    every {
      aggregationRepository.findByTypeAndPeriodRange(
        AggregationPeriodType.DAY,
        weekStart,
        LocalDate(2024, 1, 21),
      )
    } returns listOf(
      dayAggregation(day1, listOf(AggregationRankEntry("album-1", "Album One", 120L))),
      dayAggregation(
        day2,
        listOf(
          AggregationRankEntry("album-1", "Album One", 180L),
          AggregationRankEntry("album-2", "Album Two", 60L),
        ),
      ),
    )

    val result = service.handle(DomainOutboxEvent.AggregatePlaybackData(AggregationPeriodType.WEEK, weekStart))

    assertThat(result.isRight()).isTrue()
    assertThat(savedAggregation.captured.distinctAlbumCount).isEqualTo(2)
    assertThat(savedAggregation.captured.albumEntries).containsExactly(
      AggregationRankEntry("album-1", "Album One", 300L),
      AggregationRankEntry("album-2", "Album Two", 60L),
    )
    verify(exactly = 1) { aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.DAY, weekStart, LocalDate(2024, 1, 21)) }
  }

  @Test
  fun `handle AggregatePlaybackData notifies weekly digest when type is WEEK`() {
    every { currentUserResolver.userId() } returns userId
    val weekStart = LocalDate(2024, 1, 15)
    every {
      aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.DAY, weekStart, LocalDate(2024, 1, 21))
    } returns emptyList()
    val digestAggregation = PlaybackAggregation(
      type = AggregationPeriodType.WEEK,
      periodStart = weekStart,
      totalPlaybackSeconds = 600L,
      eventCount = 5L,
      distinctArtistCount = 1,
      distinctTrackCount = 1,
      distinctAlbumCount = 1,
      artistEntries = listOf(AggregationRankEntry("artist-1", "Top Artist", 600L)),
      albumEntries = emptyList(),
      trackEntries = listOf(AggregationRankEntry("track-1", "Top Track", 600L)),
      activityEntries = emptyList(),
    )
    every { aggregationRepository.findByPeriod(AggregationPeriodType.WEEK, weekStart) } returns digestAggregation

    service.handle(DomainOutboxEvent.AggregatePlaybackData(AggregationPeriodType.WEEK, weekStart))

    verify(exactly = 1) { digestNotification.notifyWeeklyDigest(digestAggregation) }
  }

  @Test
  fun `handle AggregatePlaybackData does not notify weekly digest when type is DAY`() {
    every { currentUserResolver.userId() } returns userId
    every { appPlaybackRepository.findAllBetween(any(), any()) } returns emptyList()

    service.handle(DomainOutboxEvent.AggregatePlaybackData(AggregationPeriodType.DAY, date))

    verify(exactly = 0) { digestNotification.notifyWeeklyDigest(any()) }
    verify(exactly = 0) { aggregationRepository.findByPeriod(any(), any()) }
  }

  @Test
  fun `aggregate week persists only top entries but keeps full distinct counts`() {
    every { currentUserResolver.userId() } returns userId
    val weekStart = LocalDate(2024, 1, 15)
    val savedAggregation = slot<PlaybackAggregation>()
    every { aggregationRepository.save(capture(savedAggregation)) } returns Unit
    val manyTrackEntries = (1..STORED_ENTRIES_LIMIT_PLUS_MARGIN).map { AggregationRankEntry(id = "track-$it", name = "Track $it", totalSeconds = it.toLong()) }
    every {
      aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.DAY, weekStart, LocalDate(2024, 1, 21))
    } returns listOf(dayAggregation(weekStart, trackEntries = manyTrackEntries))

    val result = service.handle(DomainOutboxEvent.AggregatePlaybackData(AggregationPeriodType.WEEK, weekStart))

    assertThat(result.isRight()).isTrue()
    assertThat(savedAggregation.captured.distinctTrackCount).isEqualTo(STORED_ENTRIES_LIMIT_PLUS_MARGIN)
    assertThat(savedAggregation.captured.trackEntries).hasSize(STORED_ENTRIES_LIMIT)
    assertThat(savedAggregation.captured.trackEntries.map { it.id }).containsExactly(
      *manyTrackEntries.sortedByDescending { it.totalSeconds }.take(STORED_ENTRIES_LIMIT).map { it.id }.toTypedArray(),
    )
  }

  @Test
  fun `enqueueRebuildAllAggregations enqueues RebuildAllAggregations event`() {
    every { currentUserResolver.userId() } returns userId

    service.enqueueRebuildAllAggregations()

    verify { outboxPort.enqueue(DomainOutboxEvent.RebuildAllAggregations()) }
  }

  @Test
  fun `enqueueRebuildAllAggregations does nothing when no users available`() {
    every { currentUserResolver.userId() } returns null

    service.enqueueRebuildAllAggregations()

    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `handle RebuildAllAggregations delegates to rebuildAllAggregations`() {
    every { currentUserResolver.userId() } returns userId
    every { appPlaybackRepository.findOldestPlayedAt() } returns null

    val result = service.handle(DomainOutboxEvent.RebuildAllAggregations())

    assertThat(result.isRight()).isTrue()
    verify { appPlaybackRepository.findOldestPlayedAt() }
  }

  private fun dayAggregation(
    periodStart: LocalDate,
    albumEntries: List<AggregationRankEntry> = emptyList(),
    trackEntries: List<AggregationRankEntry> = emptyList(),
  ) = PlaybackAggregation(
    type = AggregationPeriodType.DAY,
    periodStart = periodStart,
    totalPlaybackSeconds = albumEntries.sumOf { it.totalSeconds } + trackEntries.sumOf { it.totalSeconds },
    eventCount = (albumEntries.size + trackEntries.size).toLong(),
    distinctArtistCount = 0,
    distinctTrackCount = trackEntries.size,
    distinctAlbumCount = albumEntries.size,
    artistEntries = emptyList(),
    albumEntries = albumEntries,
    trackEntries = trackEntries,
    activityEntries = emptyList(),
  )

  private companion object {
    private const val STORED_ENTRIES_LIMIT = 25
    private const val STORED_ENTRIES_LIMIT_PLUS_MARGIN = STORED_ENTRIES_LIMIT + 5
  }
}
