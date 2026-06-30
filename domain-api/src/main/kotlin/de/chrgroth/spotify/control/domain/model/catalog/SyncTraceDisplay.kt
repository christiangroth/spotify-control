package de.chrgroth.spotify.control.domain.model.catalog

import kotlin.time.Instant

data class SyncTraceDisplay(
  val description: String,
  val triggeredAt: Instant,
)
