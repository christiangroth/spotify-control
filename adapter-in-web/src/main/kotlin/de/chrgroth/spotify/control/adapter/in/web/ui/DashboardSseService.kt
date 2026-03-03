package de.chrgroth.spotify.control.adapter.`in`.web.ui

import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.DashboardRefreshPort
import de.chrgroth.spotify.control.util.outbox.OutboxPartition
import de.chrgroth.spotify.control.util.outbox.OutboxPartitionObserver
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.MultiEmitter
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@ApplicationScoped
class DashboardSseService : DashboardRefreshPort, OutboxPartitionObserver {

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

    override fun notifyUser(userId: UserId) = emitToUser(userId.value, "refresh")

    override fun notifyAllUsers() = emittersByUser.keys.forEach { emitToUser(it, "refresh") }

    override fun onPartitionPaused(partition: OutboxPartition) = notifyAllUsers()

    override fun onPartitionActivated(partition: OutboxPartition) = notifyAllUsers()

    private fun emitToUser(userId: String, event: String) {
        emittersByUser[userId]?.forEach { runCatching { it.emit(event) } }
    }
}
