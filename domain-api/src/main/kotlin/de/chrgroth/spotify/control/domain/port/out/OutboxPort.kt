package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.outbox.AppOutboxEvent

interface OutboxPort {
    fun enqueue(event: AppOutboxEvent)
}
