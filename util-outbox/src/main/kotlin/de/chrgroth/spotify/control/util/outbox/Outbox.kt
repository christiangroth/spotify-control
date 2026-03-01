package de.chrgroth.spotify.control.util.outbox

import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@ApplicationScoped
class Outbox(
    private val repository: OutboxRepository,
    private val wakeupService: OutboxWakeupService,
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val processor = OutboxProcessor(repository) { partition, retryAfter ->
        scope.launch {
            delay(retryAfter.toMillis())
            repository.activatePartition(partition)
            wakeupService.signal(partition)
        }
    }

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

    fun processNext(partition: OutboxPartition, dispatch: (OutboxTask) -> OutboxTaskResult): Boolean =
        processor.processNext(partition, dispatch)

    fun signal(partition: OutboxPartition) = wakeupService.signal(partition)

    fun getOrCreateChannel(partition: OutboxPartition) = wakeupService.getOrCreate(partition)

    fun resetStaleProcessingTasks() = repository.resetStaleProcessingTasks()

    fun findPartition(partition: OutboxPartition) = repository.findPartition(partition)

    fun activatePartition(partition: OutboxPartition) = repository.activatePartition(partition)

    @PreDestroy
    fun onStop() {
        scope.cancel()
    }
}
