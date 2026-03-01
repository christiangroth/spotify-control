package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent

interface OutboxPort {
    fun enqueue(event: DomainOutboxEvent)
}
