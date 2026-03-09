package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.port.`in`.OutboxManagementPort
import de.chrgroth.spotify.control.domain.port.out.OutboxActivationPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class OutboxManagementAdapter(
    private val outboxActivation: OutboxActivationPort,
) : OutboxManagementPort {

    override fun activatePartition(partitionKey: String): Boolean =
        outboxActivation.activate(partitionKey)
}
