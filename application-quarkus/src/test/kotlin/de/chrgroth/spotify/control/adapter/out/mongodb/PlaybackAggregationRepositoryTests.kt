package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.model.playback.aggregation.PlaybackAggregation
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackAggregationRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class PlaybackAggregationRepositoryTests {

  @Inject
  lateinit var aggregationRepository: PlaybackAggregationRepositoryPort

  private fun aggregation(userId: UserId, type: AggregationPeriodType, periodStart: LocalDate, eventCount: Long = 1L) = PlaybackAggregation(
    userId = userId,
    type = type,
    periodStart = periodStart,
    totalPlaybackSeconds = eventCount * SECONDS_PER_EVENT,
    eventCount = eventCount,
    distinctArtistCount = 0,
    distinctTrackCount = 0,
    distinctAlbumCount = 0,
    artistEntries = emptyList(),
    albumEntries = emptyList(),
    trackEntries = emptyList(),
    activityEntries = emptyList(),
  )

  @Test
  fun `save persists aggregation and findByUserAndPeriod returns it`() {
    val userId = UserId("user-${UUID.randomUUID()}")
    val periodStart = LocalDate(2024, 1, 10)
    aggregationRepository.save(aggregation(userId, AggregationPeriodType.DAY, periodStart, eventCount = 5L))

    val result = aggregationRepository.findByUserAndPeriod(userId, AggregationPeriodType.DAY, periodStart)

    assertThat(result).isNotNull()
    assertThat(result!!.eventCount).isEqualTo(5L)
  }

  @Test
  fun `findByUserAndPeriod returns null when no aggregation exists`() {
    val result = aggregationRepository.findByUserAndPeriod(UserId("user-${UUID.randomUUID()}"), AggregationPeriodType.DAY, LocalDate(2024, 1, 1))
    assertThat(result).isNull()
  }

  @Test
  fun `findByUserTypeAndPeriodRange returns only aggregations within range for the given user and type`() {
    val userId = UserId("user-${UUID.randomUUID()}")
    val otherUserId = UserId("user-${UUID.randomUUID()}")
    aggregationRepository.save(aggregation(userId, AggregationPeriodType.DAY, LocalDate(2024, 1, 10)))
    aggregationRepository.save(aggregation(userId, AggregationPeriodType.DAY, LocalDate(2024, 1, 15)))
    aggregationRepository.save(aggregation(userId, AggregationPeriodType.DAY, LocalDate(2024, 2, 1)))
    aggregationRepository.save(aggregation(userId, AggregationPeriodType.WEEK, LocalDate(2024, 1, 15)))
    aggregationRepository.save(aggregation(otherUserId, AggregationPeriodType.DAY, LocalDate(2024, 1, 15)))

    val result = aggregationRepository.findByUserTypeAndPeriodRange(userId, AggregationPeriodType.DAY, LocalDate(2024, 1, 1), LocalDate(2024, 1, 31))

    assertThat(result.map { it.periodStart }).containsExactlyInAnyOrder(LocalDate(2024, 1, 10), LocalDate(2024, 1, 15))
  }

  @Test
  fun `findByUserTypeAndPeriodRange returns empty list when nothing matches`() {
    val result = aggregationRepository.findByUserTypeAndPeriodRange(
      UserId("user-${UUID.randomUUID()}"), AggregationPeriodType.DAY, LocalDate(2024, 1, 1), LocalDate(2024, 1, 31),
    )
    assertThat(result).isEmpty()
  }

  @Test
  fun `sumEventCountByUser sums event counts across all DAY aggregations for the user`() {
    val userId = UserId("user-${UUID.randomUUID()}")
    aggregationRepository.save(aggregation(userId, AggregationPeriodType.DAY, LocalDate(2024, 1, 10), eventCount = 3L))
    aggregationRepository.save(aggregation(userId, AggregationPeriodType.DAY, LocalDate(2024, 1, 11), eventCount = 4L))
    aggregationRepository.save(aggregation(userId, AggregationPeriodType.WEEK, LocalDate(2024, 1, 8), eventCount = 100L))

    val result = aggregationRepository.sumEventCountByUser(userId)

    assertThat(result).isEqualTo(7L)
  }

  @Test
  fun `sumEventCountByUser returns zero when user has no aggregations`() {
    val result = aggregationRepository.sumEventCountByUser(UserId("user-${UUID.randomUUID()}"))
    assertThat(result).isEqualTo(0L)
  }

  companion object {
    private const val SECONDS_PER_EVENT = 60L
  }
}
