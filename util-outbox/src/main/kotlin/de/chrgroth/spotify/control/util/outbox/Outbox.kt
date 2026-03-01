package de.chrgroth.spotify.control.util.outbox

import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class Outbox(
    private val repository: OutboxRepository,
    private val wakeupService: OutboxWakeupService,
) {

    fun enqueue(
        partition: OutboxPartition,
        event: OutboxEvent,
        payload: String,
        priority: OutboxTaskPriority = OutboxTaskPriority.NORMAL,
    ): Boolean {
        val inserted = repository.enqueue(partition, event, payload, priority)
        if (inserted) wakeupService.signal(partition)
        return inserted
    }

    fun signal(partition: OutboxPartition) = wakeupService.signal(partition)

    fun getOrCreateChannel(partition: OutboxPartition) = wakeupService.getOrCreate(partition)

    fun resetStaleProcessingTasks() = repository.resetStaleProcessingTasks()

    fun findPartition(partition: OutboxPartition) = repository.findPartition(partition)

    fun activatePartition(partition: OutboxPartition) = repository.activatePartition(partition)
}
