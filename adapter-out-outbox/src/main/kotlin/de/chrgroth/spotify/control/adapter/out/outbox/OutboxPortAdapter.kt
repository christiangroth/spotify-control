package de.chrgroth.spotify.control.adapter.out.outbox

import de.chrgroth.quarkus.outbox.domain.OutboxControllerAdapter
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
    private val outbox: OutboxControllerAdapter,
    @param:Any private val outboxTaskCountObservers: Instance<OutboxTaskCountObserver>,
) : OutboxPort {

    override fun enqueue(event: DomainOutboxEvent) {
        val inserted = outbox.enqueue(event.partition, event, event.serializePayload, event.priority)
        if (inserted) {
            logger.info { "Enqueued outbox event ${event.key} in partition ${event.partition.key}" }
            outboxTaskCountObservers.forEach { it.onOutboxTaskCountChanged() }
        } else {
            logger.info { "Skipped duplicate outbox event ${event.key} in partition ${event.partition.key}" }
        }
    }

    companion object : KLogging()
}
