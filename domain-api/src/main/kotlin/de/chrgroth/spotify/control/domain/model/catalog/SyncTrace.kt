package de.chrgroth.spotify.control.domain.model.catalog

import kotlin.time.Instant

enum class SyncTraceEntityType { ARTIST, ALBUM }

data class SyncTrace(
  val entityType: SyncTraceEntityType,
  val entityId: String,
  val cause: SyncCause,
  val triggeredAt: Instant,
)
