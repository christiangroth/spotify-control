package de.chrgroth.spotify.control.util.outbox

import arrow.core.Either

interface OutboxTaskDispatcher {
    val partitions: List<OutboxPartition>
    fun dispatch(task: OutboxTask): Either<OutboxError, Unit>
}
