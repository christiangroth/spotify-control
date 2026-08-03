package de.chrgroth.spotify.control.domain.model.playback.aggregation

import kotlinx.datetime.LocalDate

data class RollingPlaybackSummary(
  val windowStart: LocalDate,
  val totalPlaybackSeconds: Long,
  val eventCount: Long,
  val artistEntries: List<AggregationRankEntry>,
  val trackEntries: List<AggregationRankEntry>,
)
