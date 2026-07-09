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
import de.chrgroth.spotify.control.domain.model.catalog.SyncCause
import de.chrgroth.spotify.control.domain.model.catalog.SyncTrace
import de.chrgroth.spotify.control.domain.model.catalog.SyncTraceEntityType
import de.chrgroth.spotify.control.domain.model.user.UserId
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

  override fun blockArtistFromAggregation(artistId: String): Either<DomainError, Unit> {
    appArtistRepository.findByArtistIds(setOf(ArtistId(artistId))).firstOrNull()
      ?: return ArtistSettingsError.ARTIST_NOT_FOUND.left()
    logger.info { "Blocking artist $artistId from aggregation" }
    appArtistRepository.setBlockedFromAggregation(ArtistId(artistId), true)
    playbackAggregation.rebuildAllAggregations()
    return Unit.right()
  }

  override fun unblockArtistFromAggregation(artistId: String): Either<DomainError, Unit> {
    appArtistRepository.findByArtistIds(setOf(ArtistId(artistId))).firstOrNull()
      ?: return ArtistSettingsError.ARTIST_NOT_FOUND.left()
    logger.info { "Unblocking artist $artistId from aggregation" }
    appArtistRepository.setBlockedFromAggregation(ArtistId(artistId), false)
    playbackAggregation.rebuildAllAggregations()
    return Unit.right()
  }

  // --- Catalog Sync ---

  override fun syncArtistDetails(artistId: String, userId: UserId): Either<DomainError, Unit> {
    val existing = appArtistRepository.findByArtistIds(setOf(ArtistId(artistId))).firstOrNull()
    if (existing != null) {
      logger.debug { "Artist $artistId already synced, skipping" }
      return Unit.right()
    }
    val accessToken = spotifyAccessToken.getValidAccessToken()
    return spotifyCatalog.getArtist(userId, accessToken, artistId)
      .flatMap { detail ->
        if (detail != null) {
          appArtistRepository.upsertAll(listOf(detail))
          outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums(artistId, userId))
          dashboardRefresh.notifyCatalogData()
        } else {
          logger.warn { "No data returned from Spotify for artist $artistId" }
        }
        Unit.right()
      }
  }

  override fun resyncCatalog(): Either<DomainError, Unit> {
    val allArtistIds = appArtistRepository.findAll().map { it.id.value }
    val userId = currentUserResolver.userId() ?: return Unit.right()
    val playbackCatalogRequests = buildPlaybackCatalogRequests(userId)
    logger.info { "Re-syncing catalog: ${allArtistIds.size} catalog artist(s), ${playbackCatalogRequests.size} playback track(s)" }
    allArtistIds.forEach { outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums(it, userId)) }
    syncController.syncForTracks(playbackCatalogRequests, userId)
    return Unit.right()
  }

  override fun resyncArtist(artistId: String): Either<DomainError, Unit> {
    appArtistRepository.findByArtistIds(setOf(ArtistId(artistId))).firstOrNull()
      ?: return ArtistSettingsError.ARTIST_NOT_FOUND.left()
    val userId = currentUserResolver.userId() ?: run {
      logger.warn { "No users available for artist resync, skipping $artistId" }
      return Unit.right()
    }
    outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums(artistId, userId))
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

  private fun syncAlbumDetails(albumId: String): Either<DomainError, Int> {
    val userId = currentUserResolver.userId()
    if (userId == null) {
      logger.debug { "No users available, skipping syncAlbumDetails" }
      return 0.right()
    }
    val accessToken = spotifyAccessToken.getValidAccessToken()
    val knownAlbum = appAlbumRepository.findByAlbumIds(setOf(AlbumId(albumId))).firstOrNull()
    val result = if (knownAlbum != null) {
      // metadata already captured from the artist discography response, only tracks are missing
      spotifyCatalog.getAlbumTracks(userId, accessToken, knownAlbum).map { tracks -> AlbumSyncResult(knownAlbum, tracks) }
    } else {
      // fallback for albums without known metadata, e.g. events enqueued before this album was upserted
      spotifyCatalog.getAlbum(userId, accessToken, albumId)
    }
    return when (result) {
      is Either.Left -> result.value.left()
      is Either.Right -> {
        val albumResult = result.value
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
        }
        logger.info { "Synced album '${albumResult.album.title ?: albumId}' ($albumId): ${albumResult.tracks.size} track(s)" }
        1.right()
      }
    }
  }

  // --- Outbox Handlers ---

  override fun handle(event: DomainOutboxEvent.SyncArtistDetails): Either<DomainError, Unit> =
    syncArtistDetails(event.artistId, event.userId)

  override fun handle(event: DomainOutboxEvent.SyncArtistAlbums): Either<DomainError, Unit> =
    syncArtistAlbums(event.artistId, event.userId, event.nextUrl)

  override fun handle(event: DomainOutboxEvent.SyncAlbumDetails): Either<DomainError, Unit> =
    syncAlbumDetails(event.albumId).map { Unit }

  override fun handle(event: DomainOutboxEvent.ResyncCatalog): Either<DomainError, Unit> =
    resyncCatalog()

  override fun enqueueArtistAlbumsSync(partition: Int, totalPartitions: Int) {
    val allArtists = appArtistRepository.findAll()
    val userId = currentUserResolver.userId()
    if (userId == null) {
      logger.warn { "No users available for artist albums sync, skipping partition $partition/$totalPartitions" }
      return
    }
    val partitioned = allArtists.filterIndexed { idx, _ -> idx % totalPartitions == partition }
    partitioned.forEach { artist ->
      outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums(artist.id.value, userId))
    }
  }

  override fun enqueuePlaybackArtistsForSync() {
    val userId = currentUserResolver.userId()
    if (userId == null) {
      logger.warn { "No users available for playback artists sync, skipping" }
      return
    }
    val playbackCatalogRequests = buildPlaybackCatalogRequests(userId)
    val artistCount = playbackCatalogRequests.flatMap { it.artistIds }.filter { it.isNotBlank() }.distinct().size
    logger.info { "Enqueuing artist sync for $artistCount artist(s) found in playback data" }
    syncController.syncForTracks(playbackCatalogRequests, userId)
  }

  private fun buildPlaybackCatalogRequests(userId: UserId): List<CatalogSyncRequest> {
    val recentlyPlayed = recentlyPlayedRepository.findSince(userId, null)
    val partialPlayed = recentlyPartialPlayedRepository.findSince(userId, null)
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

  private fun syncArtistAlbums(artistId: String, userId: UserId, nextUrl: String?): Either<DomainError, Unit> {
    val accessToken = spotifyAccessToken.getValidAccessToken()
    return spotifyCatalog.getArtistAlbumsPage(userId, accessToken, artistId, nextUrl)
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
            outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums(artistId, userId, page.nextUrl))
          }
        }
        Unit.right()
      }
  }

  companion object : KLogging()
}
