package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.OutgoingRequestStatsObserver
import de.chrgroth.outbox.OutboxPartition
import de.chrgroth.outbox.OutboxPartitionObserver
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.MultiEmitter
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@ApplicationScoped
class HealthSseAdapter : OutboxPartitionObserver, OutgoingRequestStatsObserver {

    private val emittersByUser = ConcurrentHashMap<String, CopyOnWriteArrayList<MultiEmitter<in String>>>()

    fun stream(userId: UserId): Multi<String> = Multi.createFrom().emitter { emitter ->
        emittersByUser.getOrPut(userId.value) { CopyOnWriteArrayList() }.add(emitter)
        emitter.onTermination {
            emittersByUser.computeIfPresent(userId.value) { _, list ->
                list.remove(emitter)
                list.takeIf { it.isNotEmpty() }
            }
        }
    }

    override fun onPartitionPaused(partition: OutboxPartition) = notifyAllUsers("refresh-outbox-partitions")

    override fun onPartitionActivated(partition: OutboxPartition) = notifyAllUsers("refresh-outbox-partitions")

    override fun onRequestRecorded() = notifyAllUsers("refresh-outgoing-http-calls")

    private fun notifyAllUsers(event: String) = emittersByUser.keys.toList().forEach { emitToUser(it, event) }

    private fun emitToUser(userId: String, event: String) {
        emittersByUser[userId]?.forEach { runCatching { it.emit(event) } }
    }
}
