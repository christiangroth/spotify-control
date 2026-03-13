package de.chrgroth.spotify.control.adapter.`in`.outbox

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.`in`.CatalogPort
import de.chrgroth.spotify.control.domain.port.`in`.PlaybackPort
import de.chrgroth.spotify.control.domain.port.`in`.PlaylistPort
import de.chrgroth.spotify.control.domain.port.`in`.UserProfilePort
import de.chrgroth.spotify.control.domain.port.out.OutboxTaskCountObserver
import de.chrgroth.outbox.OutboxPartition
import de.chrgroth.outbox.OutboxTask
import de.chrgroth.outbox.OutboxTaskDispatcher
import de.chrgroth.outbox.OutboxTaskResult
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Any
import jakarta.enterprise.inject.Instance

@ApplicationScoped
@Suppress("Unused")
class DomainOutboxTaskDispatcher(
    private val playback: PlaybackPort,
    private val catalog: CatalogPort,
    private val playlist: PlaylistPort,
    private val userProfile: UserProfilePort,
    @param:Any private val outboxTaskCountObservers: Instance<OutboxTaskCountObserver>,
) : OutboxTaskDispatcher {

    override val partitions: List<OutboxPartition> = DomainOutboxPartition.all

    override fun dispatch(task: OutboxTask): OutboxTaskResult {
        try {
          val result = dispatchEvent(DomainOutboxEvent.fromKey(task.eventType, task.payload))
          if (result is OutboxTaskResult.Success) {
            outboxTaskCountObservers.forEach { it.onOutboxTaskCountChanged() }
          }
          return result
        } catch (e: IllegalArgumentException) {
            return OutboxTaskResult.Failed("Unknown event type: ${task.eventType}", e)
        }
    }

  private fun dispatchEvent(event: DomainOutboxEvent): OutboxTaskResult =
      when (event) {
      is DomainOutboxEvent.FetchCurrentlyPlaying -> playback.handle(event)
      is DomainOutboxEvent.FetchRecentlyPlayed -> playback.handle(event)
      is DomainOutboxEvent.UpdateUserProfile -> userProfile.handle(event)
      is DomainOutboxEvent.SyncPlaylistInfo -> playlist.handle(event)
      is DomainOutboxEvent.SyncPlaylistData -> playlist.handle(event)
      is DomainOutboxEvent.RebuildPlaybackData -> playback.handle(event)
      is DomainOutboxEvent.AppendPlaybackData -> playback.handle(event)
      is DomainOutboxEvent.SyncArtistDetails -> catalog.handle(event)
      is DomainOutboxEvent.SyncTrackDetails -> catalog.handle(event)
      is DomainOutboxEvent.SyncMissingArtists -> catalog.handle(event)
      is DomainOutboxEvent.SyncMissingTracks -> catalog.handle(event)
      is DomainOutboxEvent.SyncMissingAlbums -> catalog.handle(event)
      is DomainOutboxEvent.ResyncCatalog -> catalog.handle(event)
    }
}
