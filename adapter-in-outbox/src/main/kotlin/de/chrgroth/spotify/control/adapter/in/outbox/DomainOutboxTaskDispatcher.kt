package de.chrgroth.spotify.control.adapter.`in`.outbox

import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxDispatcher
import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxEvent
import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxPartition
import de.chrgroth.quarkus.outbox.domain.DispatchResult
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.`in`.CatalogPort
import de.chrgroth.spotify.control.domain.port.`in`.PlaybackPort
import de.chrgroth.spotify.control.domain.port.`in`.PlaylistCheckPort
import de.chrgroth.spotify.control.domain.port.`in`.PlaylistPort
import de.chrgroth.spotify.control.domain.port.`in`.UserProfilePort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class DomainOutboxTaskDispatcher(
    private val playback: PlaybackPort,
    private val catalog: CatalogPort,
    private val playlist: PlaylistPort,
    private val playlistCheck: PlaylistCheckPort,
    private val userProfile: UserProfilePort,
) : ApplicationOutboxDispatcher {

    override fun getAllPartitions(): List<ApplicationOutboxPartition> = DomainOutboxPartition.all

    override fun deserialize(partition: ApplicationOutboxPartition, eventType: String, payload: String): ApplicationOutboxEvent =
        DomainOutboxEvent.fromKey(eventType, payload)

    override fun dispatch(event: ApplicationOutboxEvent): DispatchResult = dispatchEvent(event as DomainOutboxEvent)

  @Suppress("CyclomaticComplexMethod")
  private fun dispatchEvent(event: DomainOutboxEvent): DispatchResult =
      when (event) {
      is DomainOutboxEvent.FetchCurrentlyPlaying -> playback.handle(event)
      is DomainOutboxEvent.FetchRecentlyPlayed -> playback.handle(event)
      is DomainOutboxEvent.UpdateUserProfile -> userProfile.handle(event)
      is DomainOutboxEvent.SyncPlaylistInfo -> playlist.handle(event)
      is DomainOutboxEvent.SyncPlaylistData -> playlist.handle(event)
      is DomainOutboxEvent.RebuildPlaybackData -> playback.handle(event)
      is DomainOutboxEvent.AppendPlaybackData -> playback.handle(event)
      is DomainOutboxEvent.SyncArtistDetails -> catalog.handle(event)
      is DomainOutboxEvent.SyncAlbumDetails -> catalog.handle(event)
      is DomainOutboxEvent.ResyncCatalog -> catalog.handle(event)
      is DomainOutboxEvent.RunPlaylistChecks -> playlistCheck.handle(event)
    }
}
