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
import mu.KLogging

data class CatalogSyncRequest(
  val trackId: String,
  val albumId: String?,
  val artistIds: List<String>,
)

@ApplicationScoped
class SyncController(
  private val appTrackRepository: AppTrackRepositoryPort,
  private val appAlbumRepository: AppAlbumRepositoryPort,
  private val appArtistRepository: AppArtistRepositoryPort,
  private val outboxPort: OutboxPort,
) {
  /**
   * For each track in the list:
   * - Checks which tracks are missing from app_track.
   * - For missing tracks: syncs their albums and all associated artists.
   */
  fun syncForTracks(tracks: List<CatalogSyncRequest>, userId: UserId) {
    if (tracks.isEmpty()) return
    val trackIds = tracks.map { TrackId(it.trackId) }.toSet()
    val existingTrackIds = appTrackRepository.findByTrackIds(trackIds).map { it.id }.toSet()
    val missingTracks = tracks.filter { TrackId(it.trackId) !in existingTrackIds }
    if (missingTracks.isEmpty()) return
    logger.info { "Found ${missingTracks.size} missing track(s) in catalog, triggering sync" }
    val albumIds = missingTracks.mapNotNull { it.albumId }.distinct()
    val artistIds = missingTracks.flatMap { it.artistIds }.distinct()
    syncAlbums(albumIds, artistIds, userId)
  }

  /**
   * Checks which albums are missing from app_album and enqueues SyncAlbumDetails.
   * Also syncs all associated artists.
   */
  fun syncAlbums(albumIds: List<String>, artistIds: List<String>, userId: UserId) {
    if (albumIds.isNotEmpty()) {
      val existingAlbumIds = appAlbumRepository.findByAlbumIds(albumIds.map { AlbumId(it) }.toSet()).map { it.id.value }.toSet()
      val newAlbumIds = albumIds.filter { it !in existingAlbumIds }
      if (newAlbumIds.isNotEmpty()) {
        logger.info { "Enqueueing SyncAlbumDetails for ${newAlbumIds.size} new album(s)" }
        newAlbumIds.forEach { outboxPort.enqueue(DomainOutboxEvent.SyncAlbumDetails(it)) }
      }
    }
    syncArtists(artistIds, userId)
  }

  /**
   * Checks which artists are missing from app_artist and enqueues SyncArtistDetails.
   */
  fun syncArtists(artistIds: List<String>, userId: UserId) {
    if (artistIds.isEmpty()) return
    val existingArtistIds = appArtistRepository.findByArtistIds(artistIds.map { ArtistId(it) }.toSet()).map { it.id.value }.toSet()
    val newArtistIds = artistIds.filter { it !in existingArtistIds }.distinct()
    if (newArtistIds.isNotEmpty()) {
      logger.info { "Enqueueing SyncArtistDetails for ${newArtistIds.size} new artist(s)" }
      newArtistIds.forEach { outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails(it, userId)) }
    }
  }

  companion object : KLogging()
}
