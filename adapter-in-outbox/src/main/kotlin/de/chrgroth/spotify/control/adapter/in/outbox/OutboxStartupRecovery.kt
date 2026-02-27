package de.chrgroth.spotify.control.adapter.`in`.outbox

import de.chrgroth.spotify.control.domain.outbox.AppOutboxPartition
import de.chrgroth.spotify.control.outbox.OutboxRepository
import de.chrgroth.spotify.control.outbox.OutboxWakeupService
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import mu.KLogging

@ApplicationScoped
class OutboxStartupRecovery(
    private val repository: OutboxRepository,
    private val wakeupService: OutboxWakeupService,
) {

    @Suppress("UnusedParameter")
    fun onStart(@Observes ev: StartupEvent) {
        repository.resetStaleProcessing()
        AppOutboxPartition.entries.forEach { partition ->
            wakeupService.signal(partition.key)
        }
        logger.info { "Outbox startup recovery complete; signalled ${AppOutboxPartition.entries.size} partition(s)" }
    }

    companion object : KLogging()
}
