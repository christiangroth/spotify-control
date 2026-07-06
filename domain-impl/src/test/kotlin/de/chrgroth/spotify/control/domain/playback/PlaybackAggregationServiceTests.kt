package de.chrgroth.spotify.control.domain.playback

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.AppPlaybackItem
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationRankEntry
import de.chrgroth.spotify.control.domain.model.playback.aggregation.PlaybackAggregation
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.playback.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackAggregationRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
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

  private val userRepository: UserRepositoryPort = mockk()
  private val appPlaybackRepository: AppPlaybackRepositoryPort = mockk()
  private val appTrackRepository: AppTrackRepositoryPort = mockk()
  private val appArtistRepository: AppArtistRepositoryPort = mockk()
  private val aggregationRepository: PlaybackAggregationRepositoryPort = mockk(relaxed = true)
  private val outboxPort: OutboxPort = mockk(relaxed = true)
  private val meterRegistry = SimpleMeterRegistry()

  private val service = PlaybackAggregationService(
    userRepository = userRepository,
    appPlaybackRepository = appPlaybackRepository,
    appTrackRepository = appTrackRepository,
    appArtistRepository = appArtistRepository,
    aggregationRepository = aggregationRepository,
    outboxPort = outboxPort,
    meterRegistry = meterRegistry,
  )

  private val userId = UserId("user-1")
  private val date = LocalDate(2024, 1, 15)
  private val syncTimestamp = Instant.fromEpochSeconds(0)

  @Test
  fun `aggregate day persists album entries and distinct album count`() {
    val savedAggregation = slot<PlaybackAggregation>()
    every { aggregationRepository.save(capture(savedAggregation)) } returns Unit
    every { appPlaybackRepository.findAllBetween(userId, any(), any()) } returns listOf(
      AppPlaybackItem(userId, Instant.fromEpochSeconds(1), "track-1", 120L),
      AppPlaybackItem(userId, Instant.fromEpochSeconds(2), "track-2", 180L),
      AppPlaybackItem(userId, Instant.fromEpochSeconds(3), "track-3", 60L),
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

    val result = service.handle(DomainOutboxEvent.AggregatePlaybackData(userId, AggregationPeriodType.DAY, date))

    assertThat(result.isRight()).isTrue()
    assertThat(savedAggregation.captured.distinctAlbumCount).isEqualTo(2)
    assertThat(savedAggregation.captured.albumEntries).containsExactly(
      AggregationRankEntry(id = "album-1", name = "Album One", totalSeconds = 300L),
      AggregationRankEntry(id = "fallback:Loose Single", name = "Loose Single", totalSeconds = 60L),
    )
  }

  @Test
  fun `aggregate week merges album entries from day aggregations`() {
    val weekStart = LocalDate(2024, 1, 15)
    val day1 = LocalDate(2024, 1, 15)
    val day2 = LocalDate(2024, 1, 16)
    val savedAggregation = slot<PlaybackAggregation>()
    every { aggregationRepository.save(capture(savedAggregation)) } returns Unit
    every {
      aggregationRepository.findByUserTypeAndPeriodRange(
        userId,
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

    val result = service.handle(DomainOutboxEvent.AggregatePlaybackData(userId, AggregationPeriodType.WEEK, weekStart))

    assertThat(result.isRight()).isTrue()
    assertThat(savedAggregation.captured.distinctAlbumCount).isEqualTo(2)
    assertThat(savedAggregation.captured.albumEntries).containsExactly(
      AggregationRankEntry("album-1", "Album One", 300L),
      AggregationRankEntry("album-2", "Album Two", 60L),
    )
    verify(exactly = 1) { aggregationRepository.findByUserTypeAndPeriodRange(userId, AggregationPeriodType.DAY, weekStart, LocalDate(2024, 1, 21)) }
  }

  private fun dayAggregation(periodStart: LocalDate, albumEntries: List<AggregationRankEntry>) = PlaybackAggregation(
    userId = userId,
    type = AggregationPeriodType.DAY,
    periodStart = periodStart,
    totalPlaybackSeconds = albumEntries.sumOf { it.totalSeconds },
    eventCount = albumEntries.size.toLong(),
    distinctArtistCount = 0,
    distinctTrackCount = 0,
    distinctAlbumCount = albumEntries.size,
    artistEntries = emptyList(),
    albumEntries = albumEntries,
    trackEntries = emptyList(),
    activityEntries = emptyList(),
  )
}
