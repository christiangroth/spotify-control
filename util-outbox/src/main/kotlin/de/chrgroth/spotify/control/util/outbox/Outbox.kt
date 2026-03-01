package de.chrgroth.spotify.control.util.outbox

import arrow.core.Either
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class Outbox(
    private val repository: OutboxRepository,
    private val wakeupService: OutboxWakeupService,
    private val meterRegistry: MeterRegistry,
) {

    private val processor = OutboxProcessor(repository)
    private val enqueuedCounters = ConcurrentHashMap<String, Counter>()
    private val processedCounters = ConcurrentHashMap<String, Counter>()
    private val failedCounters = ConcurrentHashMap<String, Counter>()

    fun enqueue(
        partition: OutboxPartition,
        event: OutboxEvent,
        payload: String,
        priority: OutboxTaskPriority = OutboxTaskPriority.NORMAL,
    ): Boolean {
        val inserted = repository.enqueue(partition, event, payload, priority)
        if (inserted) {
            wakeupService.signal(partition)
            enqueuedCounters.getOrPut(partition.key) {
                meterRegistry.counter("outbox_tasks_enqueued_total", "partition", partition.key)
            }.increment()
        }
        return inserted
    }

    fun processNext(partition: OutboxPartition, dispatch: (OutboxTask) -> Either<OutboxError, Unit>): Boolean =
        processor.processNext(partition) { task ->
            val result = dispatch(task)
            when (result) {
                is Either.Right -> processedCounters.getOrPut(partition.key) {
                    meterRegistry.counter("outbox_tasks_processed_total", "partition", partition.key)
                }.increment()
                is Either.Left -> failedCounters.getOrPut(partition.key) {
                    meterRegistry.counter("outbox_tasks_failed_total", "partition", partition.key)
                }.increment()
            }
            result
        }

    fun signal(partition: OutboxPartition) = wakeupService.signal(partition)

    fun getOrCreateChannel(partition: OutboxPartition) = wakeupService.getOrCreate(partition)

    fun resetStaleProcessingTasks() = repository.resetStaleProcessingTasks()

    fun findPartition(partition: OutboxPartition) = repository.findPartition(partition)

    fun activatePartition(partition: OutboxPartition) = repository.activatePartition(partition)
}
