package de.chrgroth.spotify.control.adapter.out.outbox

import de.chrgroth.outbox.OutboxRepository
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.OutboxActivationPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class OutboxActivationAdapter(
    private val repository: OutboxRepository,
) : OutboxActivationPort {

    override fun activate(partitionKey: String): Boolean {
        val partition = DomainOutboxPartition.all.firstOrNull { it.key == partitionKey } ?: return false
        repository.activatePartition(partition)
        return true
    }
}
