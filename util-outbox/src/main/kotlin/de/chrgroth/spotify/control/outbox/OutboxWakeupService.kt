package de.chrgroth.spotify.control.outbox

import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.channels.Channel
import mu.KLogging
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class OutboxWakeupService {

    private val channels = ConcurrentHashMap<String, Channel<Unit>>()

    fun channelFor(partitionKey: String): Channel<Unit> =
        channels.getOrPut(partitionKey) { Channel(Channel.CONFLATED) }

    fun signal(partitionKey: String) {
        channelFor(partitionKey).trySend(Unit)
        logger.debug { "Signalled outbox wakeup for partition: $partitionKey" }
    }

    companion object : KLogging()
}
