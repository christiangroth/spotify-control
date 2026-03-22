package de.chrgroth.spotify.control.adapter.out.outbox

import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxClient
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class OutboxPortAdapter(
    private val outbox: ApplicationOutboxClient,
) : OutboxPort {

    override fun enqueue(event: DomainOutboxEvent) {
        outbox.enqueue(event)
        logger.info { "Enqueued outbox event ${event.key} in partition ${event.partition.key}" }
    }

    companion object : KLogging()
}
