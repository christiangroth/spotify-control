package de.chrgroth.spotify.control.adapter.`in`.outbox

import arrow.core.Either
import arrow.core.left
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.`in`.OutboxHandlerPort
import de.chrgroth.spotify.control.util.outbox.OutboxError
import de.chrgroth.spotify.control.util.outbox.OutboxPartition
import de.chrgroth.spotify.control.util.outbox.OutboxTask
import de.chrgroth.spotify.control.util.outbox.OutboxTaskDispatcher
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused", "SwallowedException")
class DomainOutboxTaskDispatcher(
    private val handlerPort: OutboxHandlerPort,
) : OutboxTaskDispatcher {

    override val partitions: List<OutboxPartition> = DomainOutboxPartition.all

    override fun dispatch(task: OutboxTask): Either<OutboxError, Unit> {
        val event = try {
            DomainOutboxEvent.fromKey(task.eventType, task.payload)
        } catch (e: IllegalArgumentException) {
            return OutboxError("Unknown event type: ${task.eventType}").left()
        }
        return when (event) {
            is DomainOutboxEvent.FetchRecentlyPlayed -> handlerPort.handle(event)
            is DomainOutboxEvent.UpdateUserProfile -> handlerPort.handle(event)
        }
    }
}
