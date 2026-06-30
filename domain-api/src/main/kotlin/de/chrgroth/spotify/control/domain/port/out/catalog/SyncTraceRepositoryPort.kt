package de.chrgroth.spotify.control.domain.port.out.catalog

import de.chrgroth.spotify.control.domain.model.catalog.SyncTrace
import de.chrgroth.spotify.control.domain.model.catalog.SyncTraceEntityType

interface SyncTraceRepositoryPort {
  fun upsert(trace: SyncTrace)
  fun find(entityType: SyncTraceEntityType, entityId: String): SyncTrace?
}
