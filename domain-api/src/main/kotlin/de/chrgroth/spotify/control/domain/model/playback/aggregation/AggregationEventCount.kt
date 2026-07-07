package de.chrgroth.spotify.control.domain.model.playback.aggregation

import kotlinx.datetime.LocalDate

data class AggregationEventCount(
  val periodStart: LocalDate,
  val eventCount: Long,
)
