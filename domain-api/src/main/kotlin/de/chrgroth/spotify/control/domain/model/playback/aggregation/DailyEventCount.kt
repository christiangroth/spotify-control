package de.chrgroth.spotify.control.domain.model.playback.aggregation

import kotlinx.datetime.LocalDate

data class DailyEventCount(
  val periodStart: LocalDate,
  val eventCount: Long,
)
