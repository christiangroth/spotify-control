package de.chrgroth.spotify.control.adapter.out.outbox

import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxClient
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.OutboxTaskCountObserver
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Any
import jakarta.enterprise.inject.Instance
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class OutboxPortAdapter(
    private val outbox: ApplicationOutboxClient,
    @param:Any private val outboxTaskCountObservers: Instance<OutboxTaskCountObserver>,
) : OutboxPort {

    override fun enqueue(event: DomainOutboxEvent) {
        outbox.enqueue(event)
        logger.info { "Enqueued outbox event ${event.key} in partition ${event.partition.key}" }
        outboxTaskCountObservers.forEach { it.onOutboxTaskCountChanged() }
    }

    companion object : KLogging()
}
