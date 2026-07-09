package de.chrgroth.spotify.control.domain.catalog

import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.SyncCause
import de.chrgroth.spotify.control.domain.model.catalog.SyncTrace
import de.chrgroth.spotify.control.domain.model.catalog.SyncTraceEntityType
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.SyncTraceRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock

@ApplicationScoped
class SyncController(
  private val appTrackRepository: AppTrackRepositoryPort,
  private val appArtistRepository: AppArtistRepositoryPort,
  private val syncTraceRepository: SyncTraceRepositoryPort,
  private val outboxPort: OutboxPort,
) {
  /**
   * For each track in the list:
   * - Checks which tracks are missing from app_track.
   * - For missing tracks: enqueues SyncArtistDetails for any artists not yet in the catalog.
   */
  fun syncForTracks(tracks: List<CatalogSyncRequest>) {
    if (tracks.isEmpty()) return
    val trackIds = tracks.map { TrackId(it.trackId) }.toSet()
    val existingTrackIds = appTrackRepository.findByTrackIds(trackIds).map { it.id }.toSet()
    val missingTracks = tracks.filter { TrackId(it.trackId) !in existingTrackIds }
    if (missingTracks.isEmpty()) return
    val artistCauses = missingTracks.flatMap { request -> request.artistIds.map { it to request.cause } }.toMap()
    syncArtists(artistCauses)
  }

  /**
   * Checks which artists are missing from app_artist and enqueues SyncArtistDetails.
   */
  fun syncArtists(artistIds: List<String>, cause: SyncCause) {
    if (artistIds.isEmpty()) return
    syncArtists(artistIds.associateWith { cause })
  }

  private fun syncArtists(artistCauses: Map<String, SyncCause>) {
    if (artistCauses.isEmpty()) return
    val existingArtistIds = appArtistRepository.findByArtistIds(artistCauses.keys.map { ArtistId(it) }.toSet()).map { it.id.value }.toSet()
    val newArtistIds = artistCauses.keys.filter { it !in existingArtistIds }
    if (newArtistIds.isEmpty()) return
    val now = Clock.System.now()
    newArtistIds.forEach { artistId ->
      syncTraceRepository.upsert(SyncTrace(SyncTraceEntityType.ARTIST, artistId, artistCauses.getValue(artistId), now))
      outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails(artistId))
    }
  }
}
