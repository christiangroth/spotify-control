package de.chrgroth.spotify.control.adapter.`in`.outbox

import arrow.core.Either
import arrow.core.left
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.`in`.OutboxHandlerPort
import de.chrgroth.spotify.control.util.outbox.Outbox
import de.chrgroth.spotify.control.util.outbox.OutboxError
import de.chrgroth.spotify.control.util.outbox.OutboxProcessor
import de.chrgroth.spotify.control.util.outbox.OutboxTask
import io.quarkus.runtime.StartupEvent
import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mu.KLogging

@ApplicationScoped
@Suppress("Unused", "UnusedParameter", "SwallowedException")
class OutboxPartitionWorker(
    private val outboxProcessor: OutboxProcessor,
    private val outbox: Outbox,
    private val handlerPort: OutboxHandlerPort,
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    fun onStart(@Observes event: StartupEvent) {
        DomainOutboxPartition.all.forEach { partition ->
            scope.launch {
                val channel = outbox.getOrCreateChannel(partition)
                while (isActive) {
                    channel.receive()
                    var processed: Boolean
                    do {
                        processed = outboxProcessor.processNext(partition) { task ->
                            dispatch(task)
                        }
                    } while (processed && isActive)
                }
            }
            logger.info { "Outbox partition worker started for ${partition.key}" }
        }
    }

    @PreDestroy
    fun onStop() {
        scope.cancel()
        logger.info { "Outbox partition workers stopped" }
    }

    internal fun dispatch(task: OutboxTask): Either<OutboxError, Unit> {
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

    companion object : KLogging()
}
