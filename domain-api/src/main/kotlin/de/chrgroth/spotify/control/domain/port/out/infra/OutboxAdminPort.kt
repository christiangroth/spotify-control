package de.chrgroth.spotify.control.domain.port.out.infra

/**
 * Operational maintenance operations on the outbox storage that the quarkus-outbox library does
 * not expose (e.g. no wipe-all API). Kept separate from [OutboxPort] so that MongoDB access stays
 * confined to adapter-out-mongodb instead of adapter-out-outbox.
 */
interface OutboxAdminPort {
  fun wipeAll()

  /**
   * Clears overdue pending tasks (already retried at least once, with a due `nextRetryAt` in the past)
   * for [partitionKey]. Works around a quarkus-outbox defect where a failed task's retry never
   * re-signals its partition worker, so an overdue retry sits claimable but is never picked up again.
   * Deleting the stuck task lets the next regular enqueue attempt for the same deduplication key insert
   * successfully and signal the partition, unblocking it without an application restart.
   * Returns the number of tasks cleared.
   */
  fun requeueStuckTasks(partitionKey: String): Int
}
