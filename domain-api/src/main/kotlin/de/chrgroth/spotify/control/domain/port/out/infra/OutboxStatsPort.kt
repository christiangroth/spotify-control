package de.chrgroth.spotify.control.domain.port.out.infra

import de.chrgroth.spotify.control.domain.model.infra.OutboxPartitionStats

interface OutboxStatsPort {
  fun current(): List<OutboxPartitionStats>
}
