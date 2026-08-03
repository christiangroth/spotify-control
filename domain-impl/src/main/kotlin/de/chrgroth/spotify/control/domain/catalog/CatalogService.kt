package de.chrgroth.spotify.control.domain.catalog

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.ArtistSettingsError
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.catalog.AlbumSyncResult
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistSyncStatus
import de.chrgroth.spotify.control.domain.model.catalog.SyncCause
import de.chrgroth.spotify.control.domain.model.catalog.SyncTrace
import de.chrgroth.spotify.control.domain.model.catalog.SyncTraceEntityType
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.SyncTraceRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPartialPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.catalog.SpotifyCatalogPort
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackAggregationPort
import de.chrgroth.spotify.control.domain.user.CurrentUserResolver
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock
import mu.KLogging

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class CatalogService(
  private val spotifyAccessToken: SpotifyAccessTokenPort,
  private val spotifyCatalog: SpotifyCatalogPort,
  private val appArtistRepository: AppArtistRepositoryPort,
  private val appTrackRepository: AppTrackRepositoryPort,
  private val appAlbumRepository: AppAlbumRepositoryPort,
  private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort,
  private val recentlyPartialPlayedRepository: RecentlyPartialPlayedRepositoryPort,
  private val currentUserResolver: CurrentUserResolver,
  private val outboxPort: OutboxPort,
  private val playlistRepository: PlaylistRepositoryPort,
  private val playlistCheckRepository: AppPlaylistCheckRepositoryPort,
  private val dashboardRefresh: DashboardRefreshPort,
  private val syncController: SyncController,
  private val playbackAggregation: PlaybackAggregationPort,
  private val syncTraceRepository: SyncTraceRepositoryPort,
) : CatalogPort {

  // --- Artist Settings ---

  override fun findAllArtists(): List<AppArtist> = appArtistRepository.findAll()

  override fun setArtistSync(artistId: String): Either<DomainError, Unit> {
    appArtistRepository.findByArtistIds(setOf(ArtistId(artistId))).firstOrNull()
      ?: return ArtistSettingsError.ARTIST_NOT_FOUND.left()
    outboxPort.enqueue(DomainOutboxEvent.ConfirmArtistSync(artistId))
    return Unit.right()
  }

  override fun setArtistShallow(artistId: String): Either<DomainError, Unit> {
    appArtistRepository.findByArtistIds(setOf(ArtistId(artistId))).firstOrNull()
      ?: return ArtistSettingsError.ARTIST_NOT_FOUND.left()
    outboxPort.enqueue(DomainOutboxEvent.ConfirmArtistShallow(artistId))
    return Unit.right()
  }

  override fun promoteAssumptionArtistsFoundOnPlaylist(artistIds: Set<String>) {
    if (artistIds.isEmpty()) return
    val toPromote = appArtistRepository.findByArtistIds(artistIds.map { ArtistId(it) }.toSet())
      .filter { it.syncStatus == ArtistSyncStatus.SYNC_ASSUMPTION || it.syncStatus == ArtistSyncStatus.SHALLOW_ASSUMPTION }
    if (toPromote.isEmpty()) return
    toPromote.forEach { artist ->
      logger.info { "Updated sync status for artist '${artist.artistName}' (${artist.id.value}) to ${ArtistSyncStatus.SYNC}" }
      appArtistRepository.setSyncStatus(artist.id, ArtistSyncStatus.SYNC)
      outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums(artist.id.value))
    }
    playbackAggregation.rebuildAllAggregations()
  }

  // --- Catalog Sync ---

  override fun syncArtistDetails(artistId: String, fromPlaylist: Boolean): Either<DomainError, Unit> {
    val existing = appArtistRepository.findByArtistIds(setOf(ArtistId(artistId))).firstOrNull()
    if (existing != null) {
      logger.debug { "Artist $artistId already synced, skipping" }
      return Unit.right()
    }
    currentUserResolver.userId() ?: run {
      logger.warn { "No users available for artist details sync, skipping $artistId" }
      return Unit.right()
    }
    val accessToken = spotifyAccessToken.getValidAccessToken()
    val discoveryStatus = if (fromPlaylist) ArtistSyncStatus.SYNC else ArtistSyncStatus.SHALLOW_ASSUMPTION
    return spotifyCatalog.getArtist(accessToken, artistId)
      .flatMap { detail ->
        if (detail != null) {
          appArtistRepository.upsertAll(listOf(detail.copy(syncStatus = discoveryStatus)))
          if (discoveryStatus == ArtistSyncStatus.SYNC) {
            logger.info { "Newly discovered artist '${detail.artistName}' ($artistId) found on synced playlist, set to ${ArtistSyncStatus.SYNC}" }
            outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums(artistId))
          }
          dashboardRefresh.notifyCatalogData()
          outboxPort.enqueue(DomainOutboxEvent.RebuildPlaylistSettingsView())
          outboxPort.enqueue(DomainOutboxEvent.RebuildDashboardReadModel())
        } else {
          logger.warn { "No data returned from Spotify for artist $artistId" }
        }
        Unit.right()
      }
  }

  override fun resyncCatalog(): Either<DomainError, Unit> {
    val syncableArtistIds = appArtistRepository.findAll().filter { it.syncStatus.isSyncable() }.map { it.id.value }
    currentUserResolver.userId() ?: return Unit.right()
    val playbackCatalogRequests = buildPlaybackCatalogRequests()
    logger.info { "Re-syncing catalog: ${syncableArtistIds.size} catalog artist(s), ${playbackCatalogRequests.size} playback track(s)" }
    syncableArtistIds.forEach { outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums(it)) }
    syncController.syncForTracks(playbackCatalogRequests)
    return Unit.right()
  }

  override fun enqueueResyncCatalog() {
    currentUserResolver.userId() ?: return
    logger.info { "Enqueueing catalog re-sync" }
    outboxPort.enqueue(DomainOutboxEvent.ResyncCatalog())
  }

  override fun resyncArtist(artistId: String): Either<DomainError, Unit> {
    appArtistRepository.findByArtistIds(setOf(ArtistId(artistId))).firstOrNull()
      ?: return ArtistSettingsError.ARTIST_NOT_FOUND.left()
    currentUserResolver.userId() ?: run {
      logger.warn { "No users available for artist resync, skipping $artistId" }
      return Unit.right()
    }
    outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums(artistId))
    return Unit.right()
  }

  override fun wipeCatalog(): Either<DomainError, Unit> {
    logger.info { "Wiping all catalog data" }
    appArtistRepository.deleteAll()
    appAlbumRepository.deleteAll()
    appTrackRepository.deleteAll()
    playlistRepository.setAllSyncInactive()
    playlistCheckRepository.deleteAll()
    logger.info { "Catalog wipe complete" }
    return Unit.right()
  }

  override fun enqueueWipeCatalog() {
    logger.info { "Enqueueing catalog wipe" }
    outboxPort.enqueue(DomainOutboxEvent.WipeCatalog())
  }

  private fun syncAlbumDetails(albumId: String): Either<DomainError, Int> {
    currentUserResolver.userId() ?: run {
      logger.debug { "No users available, skipping syncAlbumDetails" }
      return 0.right()
    }
    val knownAlbum = appAlbumRepository.findByAlbumIds(setOf(AlbumId(albumId))).firstOrNull()
    if (knownAlbum != null && knownAlbum.artistId?.let { isArtistShallow(it) } == true) {
      logger.debug { "Album '${knownAlbum.title ?: albumId}' ($albumId): artist is shallow, skipping" }
      return 0.right()
    }
    val accessToken = spotifyAccessToken.getValidAccessToken()
    val result = if (knownAlbum != null) {
      // metadata already captured from the artist discography response, only tracks are missing
      spotifyCatalog.getAlbumTracks(accessToken, knownAlbum).map { tracks -> AlbumSyncResult(knownAlbum, tracks) }
    } else {
      // fallback for albums without known metadata, e.g. events enqueued before this album was upserted
      spotifyCatalog.getAlbum(accessToken, albumId)
    }
    return when (result) {
      is Either.Left -> result.value.left()
      is Either.Right -> {
        val albumResult = result.value
        if (knownAlbum == null && albumResult.album.artistId?.let { isArtistShallow(it) } == true) {
          logger.debug { "Album '${albumResult.album.title ?: albumId}' ($albumId): artist is shallow, skipping" }
          return 0.right()
        }
        if (albumResult.tracks.isNotEmpty()) {
          appTrackRepository.upsertAll(albumResult.tracks)
          if (knownAlbum == null) {
            appAlbumRepository.upsertAll(listOf(albumResult.album))
          }
          val expectedTracks = albumResult.album.totalTracks
          if (expectedTracks != null && albumResult.tracks.size < expectedTracks) {
            logger.warn { "Album '${albumResult.album.title ?: albumId}' ($albumId): synced ${albumResult.tracks.size} track(s) but album reports $expectedTracks total" }
          }
          dashboardRefresh.notifyCatalogData()
          outboxPort.enqueue(DomainOutboxEvent.RebuildDashboardReadModel())
        }
        logger.info { "Synced album '${albumResult.album.title ?: albumId}' ($albumId): ${albumResult.tracks.size} track(s)" }
        1.right()
      }
    }
  }

  // --- Outbox Handlers ---

  override fun handle(event: DomainOutboxEvent.SyncArtistDetails): Either<DomainError, Unit> =
    syncArtistDetails(event.artistId, event.fromPlaylist)

  override fun handle(event: DomainOutboxEvent.SyncArtistAlbums): Either<DomainError, Unit> =
    syncArtistAlbums(event.artistId, event.nextUrl)

  override fun handle(event: DomainOutboxEvent.SyncAlbumDetails): Either<DomainError, Unit> =
    syncAlbumDetails(event.albumId).map { Unit }

  override fun handle(event: DomainOutboxEvent.ConfirmArtistSync): Either<DomainError, Unit> {
    val existing = appArtistRepository.findByArtistIds(setOf(ArtistId(event.artistId))).firstOrNull()
      ?: return ArtistSettingsError.ARTIST_NOT_FOUND.left()
    logger.info { "Updated sync status for artist '${existing.artistName}' (${event.artistId}) to ${ArtistSyncStatus.SYNC}" }
    appArtistRepository.setSyncStatus(existing.id, ArtistSyncStatus.SYNC)
    outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums(event.artistId))
    playbackAggregation.rebuildAllAggregations()
    outboxPort.enqueue(DomainOutboxEvent.RebuildPlaylistSettingsView())
    return Unit.right()
  }

  override fun handle(event: DomainOutboxEvent.ConfirmArtistShallow): Either<DomainError, Unit> {
    val existing = appArtistRepository.findByArtistIds(setOf(ArtistId(event.artistId))).firstOrNull()
      ?: return ArtistSettingsError.ARTIST_NOT_FOUND.left()
    logger.info { "Updated sync status for artist '${existing.artistName}' (${event.artistId}) to ${ArtistSyncStatus.SHALLOW}" }
    appArtistRepository.setSyncStatus(existing.id, ArtistSyncStatus.SHALLOW)
    appTrackRepository.deleteByArtistId(existing.id)
    appAlbumRepository.deleteByArtistId(existing.id)
    playbackAggregation.rebuildAllAggregations()
    outboxPort.enqueue(DomainOutboxEvent.RebuildPlaylistSettingsView())
    return Unit.right()
  }

  override fun handle(event: DomainOutboxEvent.ResyncCatalog): Either<DomainError, Unit> =
    resyncCatalog()

  override fun handle(event: DomainOutboxEvent.WipeCatalog): Either<DomainError, Unit> =
    wipeCatalog()

  override fun enqueueArtistAlbumsSync(partition: Int, totalPartitions: Int) {
    val syncableArtists = appArtistRepository.findAll().filter { it.syncStatus.isSyncable() }
    currentUserResolver.userId() ?: run {
      logger.warn { "No users available for artist albums sync, skipping partition $partition/$totalPartitions" }
      return
    }
    val partitioned = syncableArtists.filterIndexed { idx, _ -> idx % totalPartitions == partition }
    partitioned.forEach { artist ->
      outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums(artist.id.value))
    }
  }

  override fun enqueuePlaybackArtistsForSync() {
    if (currentUserResolver.userId() == null) {
      logger.warn { "No users available for playback artists sync, skipping" }
      return
    }
    val playbackCatalogRequests = buildPlaybackCatalogRequests()
    val artistCount = playbackCatalogRequests.flatMap { it.artistIds }.filter { it.isNotBlank() }.distinct().size
    logger.info { "Enqueuing artist sync for $artistCount artist(s) found in playback data" }
    syncController.syncForTracks(playbackCatalogRequests)
  }

  private fun buildPlaybackCatalogRequests(): List<CatalogSyncRequest> {
    val recentlyPlayed = recentlyPlayedRepository.findSince(null)
    val partialPlayed = recentlyPartialPlayedRepository.findSince(null)
    return (
      recentlyPlayed.map { buildCatalogSyncRequest(it.trackId.value, it.artistIds) } +
        partialPlayed.map { buildCatalogSyncRequest(it.trackId.value, it.artistIds) }
    ).distinctBy { it.trackId }
  }

  private fun buildCatalogSyncRequest(trackId: String, artistIds: List<ArtistId>) = CatalogSyncRequest(
    trackId = trackId,
    artistIds = listOfNotNull(artistIds.firstOrNull()?.value?.takeIf { it.isNotBlank() }),
    cause = SyncCause.ManualResync,
  )

  private fun syncArtistAlbums(artistId: String, nextUrl: String?): Either<DomainError, Unit> {
    if (isArtistShallow(ArtistId(artistId))) {
      logger.debug { "Artist $artistId is shallow, skipping album sync" }
      return Unit.right()
    }
    val accessToken = spotifyAccessToken.getValidAccessToken()
    return spotifyCatalog.getArtistAlbumsPage(accessToken, artistId, nextUrl)
      .flatMap { page ->
        val existingAlbumIds = appAlbumRepository.findByAlbumIds(page.albums.map { it.id }.toSet()).map { it.id.value }.toSet()
        val newAlbums = page.albums.filter { it.id.value !in existingAlbumIds }
        if (newAlbums.isNotEmpty()) {
          appAlbumRepository.upsertAll(newAlbums)
          val now = Clock.System.now()
          newAlbums.forEach { album ->
            syncTraceRepository.upsert(SyncTrace(SyncTraceEntityType.ALBUM, album.id.value, SyncCause.ArtistDiscography(artistId), now))
            outboxPort.enqueue(DomainOutboxEvent.SyncAlbumDetails(album.id.value))
          }
        } else {
          logger.debug { "All ${page.albums.size} album(s) on this page for artist $artistId already in catalog" }
        }
        if (page.nextUrl != null) {
          if (newAlbums.isNotEmpty()) {
            outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums(artistId, page.nextUrl))
          }
        }
        Unit.right()
      }
  }

  private fun isArtistShallow(artistId: ArtistId): Boolean =
    appArtistRepository.findByArtistIds(setOf(artistId)).firstOrNull()?.syncStatus?.isShallow() == true

  companion object : KLogging()
}

private fun ArtistSyncStatus.isSyncable() = this == ArtistSyncStatus.SYNC || this == ArtistSyncStatus.SYNC_ASSUMPTION

private fun ArtistSyncStatus.isShallow() = this == ArtistSyncStatus.SHALLOW || this == ArtistSyncStatus.SHALLOW_ASSUMPTION
