package de.chrgroth.spotify.control.domain.model.playback.aggregation

import kotlinx.datetime.LocalDate

data class DailyPlaybackSummary(
  val periodStart: LocalDate,
  val totalPlaybackSeconds: Long,
  val eventCount: Long,
  val artistEntries: List<AggregationRankEntry>,
  val trackEntries: List<AggregationRankEntry>,
)
