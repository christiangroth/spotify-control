package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.outbox.AppOutboxEventType
import de.chrgroth.spotify.control.domain.outbox.AppOutboxPartition

interface OutboxPort {
    fun enqueue(partition: AppOutboxPartition, eventType: AppOutboxEventType, payload: Any)
}
