package de.chrgroth.spotify.control.util.outbox

interface OutboxTaskDispatcher {
    val partitions: List<OutboxPartition>
    fun dispatch(task: OutboxTask): OutboxTaskResult
}
