package de.chrgroth.spotify.control.adapter.`in`.web.ui

import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.OutgoingRequestStatsObserver
import de.chrgroth.spotify.control.util.outbox.OutboxPartition
import de.chrgroth.spotify.control.util.outbox.OutboxPartitionObserver
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.MultiEmitter
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@ApplicationScoped
class DashboardSseService : DashboardRefreshPort, OutboxPartitionObserver, OutgoingRequestStatsObserver {

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

    override fun notifyUserPlaybackData(userId: UserId) = emitToUser(userId.value, "refresh-playback-data")

    override fun notifyUserPlaylistMetadata(userId: UserId) = emitToUser(userId.value, "refresh-playlist-metadata")

    private fun notifyAllUsersOutboxPartitions() = emittersByUser.keys.forEach { emitToUser(it, "refresh-outbox-partitions") }

    private fun notifyAllUsersOutgoingHttpCalls() = emittersByUser.keys.forEach { emitToUser(it, "refresh-outgoing-http-calls") }

    override fun onPartitionPaused(partition: OutboxPartition) = notifyAllUsersOutboxPartitions()

    override fun onPartitionActivated(partition: OutboxPartition) = notifyAllUsersOutboxPartitions()

    override fun onRequestRecorded() = notifyAllUsersOutgoingHttpCalls()

    private fun emitToUser(userId: String, event: String) {
        emittersByUser[userId]?.forEach { runCatching { it.emit(event) } }
    }
}
