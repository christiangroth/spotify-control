package de.chrgroth.spotify.control.domain.port.out.playback

import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.model.playback.aggregation.DailyPlaybackSummary
import de.chrgroth.spotify.control.domain.model.playback.aggregation.PlaybackAggregation
import de.chrgroth.spotify.control.domain.model.user.UserId
import kotlinx.datetime.LocalDate

interface PlaybackAggregationRepositoryPort {
  fun save(aggregation: PlaybackAggregation)
  fun deleteAll()
  fun findByUserAndPeriod(userId: UserId, type: AggregationPeriodType, periodStart: LocalDate): PlaybackAggregation?
  fun findByUserAndPeriods(userId: UserId, periods: List<Pair<AggregationPeriodType, LocalDate>>): Map<Pair<AggregationPeriodType, LocalDate>, PlaybackAggregation>
  fun findByUserTypeAndPeriodRange(userId: UserId, type: AggregationPeriodType, from: LocalDate, to: LocalDate): List<PlaybackAggregation>
  fun findDailySummaryByUserAndPeriodRange(userId: UserId, from: LocalDate, to: LocalDate): List<DailyPlaybackSummary>
  fun sumEventCountByUser(userId: UserId): Long
}
