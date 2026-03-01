package de.chrgroth.spotify.control.adapter.out.outbox

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.util.outbox.Outbox
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class OutboxPortAdapter(
    private val outbox: Outbox,
) : OutboxPort {

    override fun enqueue(event: DomainOutboxEvent) {
        val inserted = outbox.enqueue(event.partition, event, event.toPayload(), event.priority)
        if (inserted) {
            logger.info { "Enqueued outbox event ${event.key} in partition ${event.partition.key}" }
        } else {
            logger.info { "Skipped duplicate outbox event ${event.key} in partition ${event.partition.key}" }
        }
    }

    companion object : KLogging()
}
