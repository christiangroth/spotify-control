package de.chrgroth.spotify.control.adapter.`in`.outbox

import de.chrgroth.spotify.control.domain.outbox.AppOutboxPartition
import de.chrgroth.spotify.control.util.outbox.OutboxRepository
import de.chrgroth.spotify.control.util.outbox.OutboxWakeupService
import io.quarkus.runtime.StartupEvent
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import mu.KLogging

@ApplicationScoped
@Suppress("Unused", "UnusedParameter")
class OutboxStartupRecovery(
    private val outboxRepository: OutboxRepository,
    private val wakeupService: OutboxWakeupService,
) {

    fun onStart(@Observes @Priority(1) event: StartupEvent) {
        outboxRepository.resetStaleProcessingTasks()
        AppOutboxPartition.all.forEach { partition ->
            wakeupService.signal(partition)
        }
        logger.info { "Outbox startup recovery complete, signalled ${AppOutboxPartition.all.size} partition(s)" }
    }

    companion object : KLogging()
}
