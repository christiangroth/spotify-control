package de.chrgroth.spotify.control.adapter.`in`.outbox

import de.chrgroth.quarkus.outbox.domain.event.OutboxPartitionActivatedEvent
import de.chrgroth.quarkus.outbox.domain.event.OutboxPartitionPausedEvent
import de.chrgroth.spotify.control.domain.port.out.OutboxPartitionObserver
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.enterprise.inject.Any
import jakarta.enterprise.inject.Instance

@ApplicationScoped
@Suppress("Unused")
class OutboxPartitionEventAdapter(
    @param:Any private val observers: Instance<OutboxPartitionObserver>,
) {

    fun onPartitionPaused(@Observes event: OutboxPartitionPausedEvent) {
        val reason = if (event.reason.isBlank()) "unknown" else event.reason
        observers.forEach { it.onPartitionPaused(event.partition.key, reason) }
    }

    fun onPartitionActivated(@Observes event: OutboxPartitionActivatedEvent) {
        observers.forEach { it.onPartitionActivated(event.partition.key) }
    }
}
