package de.chrgroth.spotify.control.domain.port.out.infra

import de.chrgroth.spotify.control.domain.model.OutboxPartitionStats
import de.chrgroth.spotify.control.domain.model.OutboxTask
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent

interface OutboxPort {
  fun enqueue(event: DomainOutboxEvent)
  fun getPartitionStats(): List<OutboxPartitionStats>
  fun getTasksByPartition(partitionKey: String): List<OutboxTask>
}
