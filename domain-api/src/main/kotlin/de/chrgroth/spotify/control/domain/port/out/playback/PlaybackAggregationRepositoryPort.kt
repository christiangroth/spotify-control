package de.chrgroth.spotify.control.domain.port.out.playback

import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.model.playback.aggregation.DailyEventCount
import de.chrgroth.spotify.control.domain.model.playback.aggregation.PlaybackAggregation
import de.chrgroth.spotify.control.domain.model.playback.aggregation.RollingPlaybackSummary
import kotlinx.datetime.LocalDate

interface PlaybackAggregationRepositoryPort {
  fun save(aggregation: PlaybackAggregation)
  fun deleteAll()
  fun findByPeriod(type: AggregationPeriodType, periodStart: LocalDate): PlaybackAggregation?
  fun findByPeriods(periods: List<Pair<AggregationPeriodType, LocalDate>>, topEntriesLimit: Int): Map<Pair<AggregationPeriodType, LocalDate>, PlaybackAggregation>
  fun findByTypeAndPeriodRange(type: AggregationPeriodType, from: LocalDate, to: LocalDate): List<PlaybackAggregation>
  fun findDailyEventCountsByPeriodRange(from: LocalDate, to: LocalDate): List<DailyEventCount>
  fun sumEventCount(): Long
  fun saveRollingSummary(summary: RollingPlaybackSummary)
  fun findRollingSummary(): RollingPlaybackSummary?
}
