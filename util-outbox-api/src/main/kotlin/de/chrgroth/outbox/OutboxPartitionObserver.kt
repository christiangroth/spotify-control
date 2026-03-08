package de.chrgroth.outbox

interface OutboxPartitionObserver {
    fun onPartitionPaused(partition: OutboxPartition)
    fun onPartitionActivated(partition: OutboxPartition)
    fun onTaskEnqueued(partition: OutboxPartition) {}
}
