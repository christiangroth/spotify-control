package de.chrgroth.spotify.control.domain.catalog

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class SyncController(
  private val appTrackRepository: AppTrackRepositoryPort,
  private val appArtistRepository: AppArtistRepositoryPort,
  private val appAlbumRepository: AppAlbumRepositoryPort,
  private val outboxPort: OutboxPort,
) {
  /**
   * For each track in the list:
   * - Checks which tracks are missing from app_track.
   * - For missing tracks: enqueues SyncArtistDetails for any artists not yet in the catalog,
   *   and SyncAlbumDetails for the specific album the track belongs to, if not yet in the catalog.
   * This intentionally does not crawl an artist's full discography (SyncArtistAlbums) - only the
   * album tied to the actual event is fetched, to keep the sync volume proportional to playback/
   * playlist activity instead of to each new artist's entire back catalog.
   */
  fun syncForTracks(tracks: List<CatalogSyncRequest>, userId: UserId) {
    if (tracks.isEmpty()) return
    val trackIds = tracks.map { TrackId(it.trackId) }.toSet()
    val existingTrackIds = appTrackRepository.findByTrackIds(trackIds).map { it.id }.toSet()
    val missingTracks = tracks.filter { TrackId(it.trackId) !in existingTrackIds }
    if (missingTracks.isEmpty()) return
    val artistIds = missingTracks.flatMap { it.artistIds }.distinct()
    syncArtists(artistIds, userId)
    val albumIds = missingTracks.mapNotNull { it.albumId }.distinct()
    syncAlbums(albumIds)
  }

  /**
   * Checks which artists are missing from app_artist and enqueues SyncArtistDetails.
   */
  fun syncArtists(artistIds: List<String>, userId: UserId) {
    if (artistIds.isEmpty()) return
    val existingArtistIds = appArtistRepository.findByArtistIds(artistIds.map { ArtistId(it) }.toSet()).map { it.id.value }.toSet()
    val newArtistIds = artistIds.filter { it !in existingArtistIds }.distinct()
    if (newArtistIds.isNotEmpty()) {
      newArtistIds.forEach { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails(it, userId)) }
    }
  }

  /**
   * Checks which albums are missing from app_album and enqueues SyncAlbumDetails.
   */
  fun syncAlbums(albumIds: List<String>) {
    if (albumIds.isEmpty()) return
    val existingAlbumIds = appAlbumRepository.findByAlbumIds(albumIds.map { AlbumId(it) }.toSet()).map { it.id.value }.toSet()
    val newAlbumIds = albumIds.filter { it !in existingAlbumIds }.distinct()
    if (newAlbumIds.isNotEmpty()) {
      newAlbumIds.forEach { outboxPort.enqueue(DomainOutboxEvent.SyncAlbumDetails(it)) }
    }
  }
}
