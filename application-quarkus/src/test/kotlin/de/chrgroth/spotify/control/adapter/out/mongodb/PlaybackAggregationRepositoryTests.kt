package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationRankEntry
import de.chrgroth.spotify.control.domain.model.playback.aggregation.PlaybackAggregation
import de.chrgroth.spotify.control.domain.model.playback.aggregation.RollingPlaybackSummary
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackAggregationRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@QuarkusTest
class PlaybackAggregationRepositoryTests {

  @Inject
  lateinit var aggregationRepository: PlaybackAggregationRepositoryPort

  private fun aggregation(type: AggregationPeriodType, periodStart: LocalDate, eventCount: Long = 1L) = PlaybackAggregation(
    type = type,
    periodStart = periodStart,
    totalPlaybackSeconds = eventCount * SECONDS_PER_EVENT,
    eventCount = eventCount,
    distinctArtistCount = 0,
    distinctTrackCount = 0,
    distinctAlbumCount = 0,
    artistEntries = listOf(AggregationRankEntry(id = "artist-1", name = "Artist One", totalSeconds = eventCount * SECONDS_PER_EVENT)),
    albumEntries = listOf(AggregationRankEntry(id = "album-1", name = "Album One", totalSeconds = eventCount * SECONDS_PER_EVENT)),
    trackEntries = listOf(AggregationRankEntry(id = "track-1", name = "Track One", totalSeconds = eventCount * SECONDS_PER_EVENT)),
    activityEntries = emptyList(),
  )

  @BeforeEach
  fun cleanUp() {
    // single-user application: the collection is no longer scoped per user, so tests reset it explicitly for isolation
    aggregationRepository.deleteAll()
  }

  @Test
  fun `save persists aggregation and findByPeriod returns it`() {
    val periodStart = LocalDate(2024, 1, 10)
    aggregationRepository.save(aggregation(AggregationPeriodType.DAY, periodStart, eventCount = 5L))

    val result = aggregationRepository.findByPeriod(AggregationPeriodType.DAY, periodStart)

    assertThat(result).isNotNull()
    assertThat(result!!.eventCount).isEqualTo(5L)
  }

  @Test
  fun `findByPeriod returns null when no aggregation exists`() {
    val result = aggregationRepository.findByPeriod(AggregationPeriodType.DAY, LocalDate(2024, 1, 1))
    assertThat(result).isNull()
  }

  @Test
  fun `findByPeriods resolves multiple type period-start pairs in a single batch`() {
    aggregationRepository.save(aggregation(AggregationPeriodType.DAY, LocalDate(2024, 1, 10), eventCount = 3L))
    aggregationRepository.save(aggregation(AggregationPeriodType.WEEK, LocalDate(2024, 1, 8), eventCount = 5L))

    val result = aggregationRepository.findByPeriods(
      listOf(
        AggregationPeriodType.DAY to LocalDate(2024, 1, 10),
        AggregationPeriodType.WEEK to LocalDate(2024, 1, 8),
        AggregationPeriodType.MONTH to LocalDate(2024, 1, 1),
      ),
      topEntriesLimit = 5,
    )

    assertThat(result).hasSize(2)
    assertThat(result[AggregationPeriodType.DAY to LocalDate(2024, 1, 10)]?.eventCount).isEqualTo(3L)
    assertThat(result[AggregationPeriodType.WEEK to LocalDate(2024, 1, 8)]?.eventCount).isEqualTo(5L)
    assertThat(result[AggregationPeriodType.MONTH to LocalDate(2024, 1, 1)]).isNull()
  }

  @Test
  fun `findByPeriods returns empty map for empty input`() {
    val result = aggregationRepository.findByPeriods(emptyList(), topEntriesLimit = 5)
    assertThat(result).isEmpty()
  }

  @Test
  fun `findByPeriods slices rank entries to topEntriesLimit while keeping distinct counts intact`() {
    val periodStart = LocalDate(2024, 1, 10)
    aggregationRepository.save(
      aggregation(AggregationPeriodType.DAY, periodStart).let {
        it.copy(
          distinctArtistCount = 3,
          artistEntries = listOf(
            AggregationRankEntry(id = "artist-1", name = "Artist One", totalSeconds = 300L),
            AggregationRankEntry(id = "artist-2", name = "Artist Two", totalSeconds = 200L),
            AggregationRankEntry(id = "artist-3", name = "Artist Three", totalSeconds = 100L),
          ),
        )
      },
    )

    val result = aggregationRepository.findByPeriods(listOf(AggregationPeriodType.DAY to periodStart), topEntriesLimit = 2)

    val aggregation = result.getValue(AggregationPeriodType.DAY to periodStart)
    assertThat(aggregation.distinctArtistCount).isEqualTo(3)
    assertThat(aggregation.artistEntries).extracting("id").containsExactly("artist-1", "artist-2")
  }

  @Test
  fun `findByTypeAndPeriodRange returns only aggregations within range for the given type`() {
    aggregationRepository.save(aggregation(AggregationPeriodType.DAY, LocalDate(2024, 1, 10)))
    aggregationRepository.save(aggregation(AggregationPeriodType.DAY, LocalDate(2024, 1, 15)))
    aggregationRepository.save(aggregation(AggregationPeriodType.DAY, LocalDate(2024, 2, 1)))
    aggregationRepository.save(aggregation(AggregationPeriodType.WEEK, LocalDate(2024, 1, 15)))

    val result = aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.DAY, LocalDate(2024, 1, 1), LocalDate(2024, 1, 31))

    assertThat(result.map { it.periodStart }).containsExactlyInAnyOrder(LocalDate(2024, 1, 10), LocalDate(2024, 1, 15))
  }

  @Test
  fun `findByTypeAndPeriodRange returns empty list when nothing matches`() {
    val result = aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.DAY, LocalDate(2024, 1, 1), LocalDate(2024, 1, 31))
    assertThat(result).isEmpty()
  }

  @Test
  fun `findDailyEventCountsByPeriodRange returns only DAY aggregations within range`() {
    aggregationRepository.save(aggregation(AggregationPeriodType.DAY, LocalDate(2024, 1, 10)))
    aggregationRepository.save(aggregation(AggregationPeriodType.DAY, LocalDate(2024, 1, 15)))
    aggregationRepository.save(aggregation(AggregationPeriodType.DAY, LocalDate(2024, 2, 1)))
    aggregationRepository.save(aggregation(AggregationPeriodType.WEEK, LocalDate(2024, 1, 15)))

    val result = aggregationRepository.findDailyEventCountsByPeriodRange(LocalDate(2024, 1, 1), LocalDate(2024, 1, 31))

    assertThat(result.map { it.periodStart }).containsExactlyInAnyOrder(LocalDate(2024, 1, 10), LocalDate(2024, 1, 15))
  }

  @Test
  fun `findDailyEventCountsByPeriodRange maps event count`() {
    aggregationRepository.save(aggregation(AggregationPeriodType.DAY, LocalDate(2024, 1, 10), eventCount = 5L))

    val result = aggregationRepository.findDailyEventCountsByPeriodRange(LocalDate(2024, 1, 1), LocalDate(2024, 1, 31))

    assertThat(result).hasSize(1)
    assertThat(result.single().eventCount).isEqualTo(5L)
  }

  @Test
  fun `findDailyEventCountsByPeriodRange returns empty list when nothing matches`() {
    val result = aggregationRepository.findDailyEventCountsByPeriodRange(LocalDate(2024, 1, 1), LocalDate(2024, 1, 31))
    assertThat(result).isEmpty()
  }

  @Test
  fun `saveRollingSummary persists summary and findRollingSummary returns it`() {
    val summary = RollingPlaybackSummary(
      windowStart = LocalDate(2024, 1, 1),
      totalPlaybackSeconds = 300L,
      eventCount = 5L,
      artistEntries = listOf(AggregationRankEntry(id = "artist-1", name = "Artist One", totalSeconds = 300L)),
      trackEntries = listOf(AggregationRankEntry(id = "track-1", name = "Track One", totalSeconds = 300L)),
    )

    aggregationRepository.saveRollingSummary(summary)
    val result = aggregationRepository.findRollingSummary()

    assertThat(result).isEqualTo(summary)
  }

  @Test
  fun `saveRollingSummary overwrites the previous rolling summary instead of creating a new document`() {
    aggregationRepository.saveRollingSummary(
      RollingPlaybackSummary(
        windowStart = LocalDate(2024, 1, 1),
        totalPlaybackSeconds = 100L,
        eventCount = 1L,
        artistEntries = emptyList(),
        trackEntries = emptyList(),
      ),
    )
    val updated = RollingPlaybackSummary(
      windowStart = LocalDate(2024, 1, 2),
      totalPlaybackSeconds = 200L,
      eventCount = 2L,
      artistEntries = emptyList(),
      trackEntries = emptyList(),
    )
    aggregationRepository.saveRollingSummary(updated)

    val result = aggregationRepository.findRollingSummary()

    assertThat(result).isEqualTo(updated)
  }

  @Test
  fun `findRollingSummary returns null when no rolling summary exists`() {
    val result = aggregationRepository.findRollingSummary()
    assertThat(result).isNull()
  }

  @Test
  fun `sumEventCount sums event counts across all DAY aggregations`() {
    aggregationRepository.save(aggregation(AggregationPeriodType.DAY, LocalDate(2024, 1, 10), eventCount = 3L))
    aggregationRepository.save(aggregation(AggregationPeriodType.DAY, LocalDate(2024, 1, 11), eventCount = 4L))
    aggregationRepository.save(aggregation(AggregationPeriodType.WEEK, LocalDate(2024, 1, 8), eventCount = 100L))

    val result = aggregationRepository.sumEventCount()

    assertThat(result).isEqualTo(7L)
  }

  @Test
  fun `sumEventCount returns zero when no aggregations exist`() {
    val result = aggregationRepository.sumEventCount()
    assertThat(result).isEqualTo(0L)
  }

  companion object {
    private const val SECONDS_PER_EVENT = 60L
  }
}
