package de.chrgroth.spotify.control.adapter.`in`.outbox

import de.chrgroth.spotify.control.domain.outbox.AppOutboxPartition
import de.chrgroth.spotify.control.util.outbox.OutboxPartitionStatus
import de.chrgroth.spotify.control.util.outbox.OutboxRepository
import de.chrgroth.spotify.control.util.outbox.OutboxWakeupService
import io.quarkus.runtime.StartupEvent
import jakarta.annotation.PreDestroy
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KLogging
import java.time.Instant

@ApplicationScoped
@Suppress("Unused", "UnusedParameter")
class OutboxStartupRecovery(
    private val outboxRepository: OutboxRepository,
    private val wakeupService: OutboxWakeupService,
) {

    private var scope: CoroutineScope? = null

    fun onStart(@Observes @Priority(1) event: StartupEvent) {
        scope = CoroutineScope(Dispatchers.IO)
        outboxRepository.resetStaleProcessingTasks()
        val now = Instant.now()
        AppOutboxPartition.all.forEach { partition ->
            val partitionInfo = outboxRepository.findPartition(partition)
            // partitionInfo.status is persisted as a String; compare via .name
            if (partitionInfo?.status == OutboxPartitionStatus.PAUSED.name) {
                val pausedUntil = partitionInfo.pausedUntil
                if (pausedUntil != null && pausedUntil.isAfter(now)) {
                    val delayMs = pausedUntil.toEpochMilli() - now.toEpochMilli()
                    scope!!.launch {
                        delay(delayMs)
                        outboxRepository.activatePartition(partition)
                        wakeupService.signal(partition)
                        logger.info { "Resumed partition ${partition.key} after delayed activation" }
                    }
                    logger.info { "Partition ${partition.key} still paused until $pausedUntil, scheduled delayed activation" }
                } else {
                    outboxRepository.activatePartition(partition)
                    wakeupService.signal(partition)
                    logger.info { "Reactivated expired paused partition ${partition.key}" }
                }
            } else {
                wakeupService.signal(partition)
            }
        }
        logger.info { "Outbox startup recovery complete for ${AppOutboxPartition.all.size} partition(s)" }
    }

    @PreDestroy
    fun onStop() {
        scope?.cancel()
    }

    companion object : KLogging()
}
