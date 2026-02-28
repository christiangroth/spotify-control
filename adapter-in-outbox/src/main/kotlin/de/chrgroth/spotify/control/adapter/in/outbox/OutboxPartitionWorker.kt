package de.chrgroth.spotify.control.adapter.`in`.outbox

import arrow.core.Either
import arrow.core.left
import de.chrgroth.spotify.control.domain.outbox.AppOutboxEvent
import de.chrgroth.spotify.control.domain.outbox.AppOutboxPartition
import de.chrgroth.spotify.control.domain.port.`in`.OutboxHandlerPort
import de.chrgroth.spotify.control.util.outbox.OutboxError
import de.chrgroth.spotify.control.util.outbox.OutboxProcessor
import de.chrgroth.spotify.control.util.outbox.OutboxTask
import de.chrgroth.spotify.control.util.outbox.OutboxWakeupService
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
    private val wakeupService: OutboxWakeupService,
    private val handlerPort: OutboxHandlerPort,
) {

    private var scope: CoroutineScope? = null

    fun onStart(@Observes event: StartupEvent) {
        val workerScope = CoroutineScope(Dispatchers.IO)
        scope = workerScope
        AppOutboxPartition.all.forEach { partition ->
            workerScope.launch {
                val channel = wakeupService.getOrCreate(partition)
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
        }
        logger.info { "Outbox partition workers started for ${AppOutboxPartition.all.size} partition(s)" }
    }

    @PreDestroy
    fun onStop() {
        scope?.cancel()
        logger.info { "Outbox partition workers stopped" }
    }

    private fun dispatch(task: OutboxTask): Either<OutboxError, Unit> {
        val event = try {
            AppOutboxEvent.fromKey(task.eventType)
        } catch (e: IllegalArgumentException) {
            return OutboxError("Unknown event type: ${task.eventType}").left()
        }
        return when (event) {
            is AppOutboxEvent.FetchRecentlyPlayed -> handlerPort.handleFetchRecentlyPlayed()
            is AppOutboxEvent.UpdateUserProfiles -> handlerPort.handleUpdateUserProfiles()
        }
    }

    companion object : KLogging()
}
