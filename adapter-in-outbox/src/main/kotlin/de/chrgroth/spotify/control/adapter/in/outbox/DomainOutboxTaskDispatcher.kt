package de.chrgroth.spotify.control.adapter.`in`.outbox

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.`in`.OutboxHandlerPort
import de.chrgroth.spotify.control.util.outbox.OutboxPartition
import de.chrgroth.spotify.control.util.outbox.OutboxTask
import de.chrgroth.spotify.control.util.outbox.OutboxTaskDispatcher
import de.chrgroth.spotify.control.util.outbox.OutboxTaskResult
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class DomainOutboxTaskDispatcher(
    private val handlerPort: OutboxHandlerPort,
) : OutboxTaskDispatcher {

    override val partitions: List<OutboxPartition> = DomainOutboxPartition.all

    override fun dispatch(task: OutboxTask): OutboxTaskResult {
        val event = try {
            DomainOutboxEvent.fromKey(task.eventType, task.payload)
        } catch (e: IllegalArgumentException) {
            return OutboxTaskResult.Failed("Unknown event type: ${task.eventType}", e)
        }
        return when (event) {
            is DomainOutboxEvent.FetchCurrentlyPlaying -> handlerPort.handle(event)
            is DomainOutboxEvent.FetchRecentlyPlayed -> handlerPort.handle(event)
            is DomainOutboxEvent.UpdateUserProfile -> handlerPort.handle(event)
            is DomainOutboxEvent.SyncPlaylistInfo -> handlerPort.handle(event)
            is DomainOutboxEvent.SyncPlaylistData -> handlerPort.handle(event)
            is DomainOutboxEvent.RebuildPlaybackData -> handlerPort.handle(event)
            is DomainOutboxEvent.AppendPlaybackData -> handlerPort.handle(event)
        }
    }
}
