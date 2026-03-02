package de.chrgroth.spotify.control.util.outbox

import io.quarkus.runtime.StartupEvent
import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mu.KLogging

@ApplicationScoped
@Suppress("Unused", "UnusedParameter", "SwallowedException")
class OutboxPartitionWorker(
    private val outbox: Outbox,
    private val dispatcher: OutboxTaskDispatcher,
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    fun onStart(@Observes event: StartupEvent) {
        dispatcher.partitions.forEach { partition ->
            scope.launch {
                val channel = outbox.getOrCreateChannel(partition)
                while (isActive) {
                    channel.receive()
                    var processed: Boolean
                    do {
                        try {
                            processed = outbox.processNext(partition) { task ->
                                dispatcher.dispatch(task)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logger.error(e) { "Unexpected error processing outbox partition ${partition.key}" }
                            processed = false
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

    companion object : KLogging()
}
