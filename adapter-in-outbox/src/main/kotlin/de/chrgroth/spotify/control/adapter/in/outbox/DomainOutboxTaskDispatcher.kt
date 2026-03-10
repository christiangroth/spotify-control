package de.chrgroth.spotify.control.adapter.`in`.outbox

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.`in`.CatalogPort
import de.chrgroth.spotify.control.domain.port.`in`.PlaybackPort
import de.chrgroth.spotify.control.domain.port.`in`.PlaylistPort
import de.chrgroth.spotify.control.domain.port.`in`.UserProfilePort
import de.chrgroth.outbox.OutboxPartition
import de.chrgroth.outbox.OutboxTask
import de.chrgroth.outbox.OutboxTaskDispatcher
import de.chrgroth.outbox.OutboxTaskResult
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class DomainOutboxTaskDispatcher(
    private val playback: PlaybackPort,
    private val catalog: CatalogPort,
    private val playlist: PlaylistPort,
    private val userProfile: UserProfilePort,
) : OutboxTaskDispatcher {

    override val partitions: List<OutboxPartition> = DomainOutboxPartition.all

    override fun dispatch(task: OutboxTask): OutboxTaskResult {
        val event = try {
            DomainOutboxEvent.fromKey(task.eventType, task.payload)
        } catch (e: IllegalArgumentException) {
            return OutboxTaskResult.Failed("Unknown event type: ${task.eventType}", e)
        }
        return when (event) {
            is DomainOutboxEvent.FetchCurrentlyPlaying -> playback.handle(event)
            is DomainOutboxEvent.FetchRecentlyPlayed -> playback.handle(event)
            is DomainOutboxEvent.UpdateUserProfile -> userProfile.handle(event)
            is DomainOutboxEvent.SyncPlaylistInfo -> playlist.handle(event)
            is DomainOutboxEvent.SyncPlaylistData -> playlist.handle(event)
            is DomainOutboxEvent.RebuildPlaybackData -> playback.handle(event)
            is DomainOutboxEvent.AppendPlaybackData -> playback.handle(event)
            is DomainOutboxEvent.EnrichArtistDetails -> catalog.handle(event)
            is DomainOutboxEvent.EnrichTrackDetails -> catalog.handle(event)
            is DomainOutboxEvent.EnrichAlbumDetails -> catalog.handle(event)
        }
    }
}
