package de.chrgroth.spotify.control.adapter.`in`.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import de.chrgroth.spotify.control.domain.outbox.AppOutboxEventType
import de.chrgroth.spotify.control.domain.outbox.AppOutboxPartition
import de.chrgroth.spotify.control.domain.outbox.PollRecentlyPlayedPayload
import de.chrgroth.spotify.control.domain.port.out.OutboxHandlerPort
import de.chrgroth.spotify.control.outbox.OutboxProcessor
import de.chrgroth.spotify.control.outbox.OutboxRepository
import de.chrgroth.spotify.control.outbox.OutboxTask
import de.chrgroth.spotify.control.outbox.OutboxWakeupService
import de.chrgroth.spotify.control.outbox.RetryPolicy
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mu.KLogging

@ApplicationScoped
class OutboxPartitionWorker(
    private val repository: OutboxRepository,
    private val wakeupService: OutboxWakeupService,
    private val handlerPort: OutboxHandlerPort,
    private val objectMapper: ObjectMapper,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Suppress("UnusedParameter")
    fun onStart(@Observes ev: StartupEvent) {
        AppOutboxPartition.entries.forEach { partition ->
            val processor = OutboxProcessor(repository, RetryPolicy())
            val channel = wakeupService.channelFor(partition.key)
            scope.launch {
                logger.info { "Started outbox worker for partition: ${partition.key}" }
                while (isActive) {
                    channel.receive()
                    do {
                        val processed = processor.processNext(partition.key) { task ->
                            dispatch(task)
                        }
                    } while (processed && isActive)
                }
                logger.info { "Outbox worker stopped for partition: ${partition.key}" }
            }
        }
    }

    @Suppress("UnusedParameter")
    fun onStop(@Observes ev: ShutdownEvent) {
        logger.info { "Stopping outbox workers" }
        scope.cancel()
    }

    private fun dispatch(task: OutboxTask): Result<Unit> =
        runCatching {
            when (AppOutboxEventType.fromKey(task.eventType)) {
                AppOutboxEventType.PollRecentlyPlayed -> {
                    val payload = objectMapper.readValue(task.payload, PollRecentlyPlayedPayload::class.java)
                    handlerPort.handlePollRecentlyPlayed(payload).fold(
                        ifLeft = { err -> throw IllegalStateException(err.message) },
                        ifRight = { },
                    )
                }
            }
        }

    companion object : KLogging()
}
