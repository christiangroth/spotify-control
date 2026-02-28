package de.chrgroth.spotify.control.adapter.out.outbox

import de.chrgroth.spotify.control.domain.outbox.AppOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.util.outbox.OutboxRepository
import de.chrgroth.spotify.control.util.outbox.OutboxWakeupService
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class OutboxPortAdapter(
    private val repository: OutboxRepository,
    private val wakeupService: OutboxWakeupService,
) : OutboxPort {

    override fun enqueue(event: AppOutboxEvent) {
        val inserted = repository.enqueue(event.partition, event, event.toPayload(), event.priority)
        if (inserted) {
            logger.debug { "Enqueued outbox event ${event.key} in partition ${event.partition.key}" }
            wakeupService.signal(event.partition)
        } else {
            logger.debug { "Skipped duplicate outbox event ${event.key} in partition ${event.partition.key}" }
        }
    }

    companion object : KLogging()
}
