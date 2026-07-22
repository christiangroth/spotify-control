package de.chrgroth.spotify.control.domain.port.`in`.infra

import de.chrgroth.spotify.control.domain.model.infra.OutboxViewerPartition

interface OutboxViewerPort {
  fun getPartitions(): List<OutboxViewerPartition>
  fun wipeAll()

  /**
   * Clears stuck, overdue-retry tasks in [partitionKey] so they get freshly re-enqueued (and the
   * partition re-signalled) on the next regular attempt. Returns the number of tasks cleared.
   */
  fun requeueStuckTasks(partitionKey: String): Int
}
