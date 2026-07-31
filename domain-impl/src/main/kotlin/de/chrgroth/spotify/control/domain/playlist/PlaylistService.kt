package de.chrgroth.spotify.control.domain.playlist

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.PlaylistSyncError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.model.playlist.ArtistStats
import de.chrgroth.spotify.control.domain.model.playlist.MissingArtist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.model.catalog.SyncCause
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.SpotifyCatalogPort
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistSyncNotificationPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.catalog.SyncController
import de.chrgroth.spotify.control.domain.catalog.CatalogSyncRequest
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogPort
import de.chrgroth.spotify.control.domain.port.out.playlist.SpotifyPlaylistPort
import de.chrgroth.spotify.control.domain.user.CurrentUserResolver
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import mu.KLogging
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class PlaylistService(
  private val currentUserResolver: CurrentUserResolver,
  private val playlistRepository: PlaylistRepositoryPort,
  private val spotifyAccessToken: SpotifyAccessTokenPort,
  private val spotifyPlaylist: SpotifyPlaylistPort,
  private val outboxPort: OutboxPort,
  private val dashboardRefresh: DashboardRefreshPort,
  private val playlistCheckRepository: AppPlaylistCheckRepositoryPort,
  private val syncController: SyncController,
  private val catalogPort: CatalogPort,
  private val appArtistRepository: AppArtistRepositoryPort,
  private val spotifyCatalog: SpotifyCatalogPort,
  private val meterRegistry: MeterRegistry,
  private val syncNotification: PlaylistSyncNotificationPort,
) : PlaylistPort {

  private val lastSyncJobSuccessTimestamp = AtomicLong()

  @Suppress("UnusedParameter")
  fun onStartup(@Observes event: StartupEvent) {
    Gauge.builder("app.playlist.sync_job_last_success_timestamp", lastSyncJobSuccessTimestamp) { it.get().toDouble() }
      .description("Epoch second timestamp of the last successful playlist sync job run")
      .register(meterRegistry)
  }

  override fun getPlaylists(): List<PlaylistInfo> {
    currentUserResolver.userId() ?: return emptyList()
    return playlistRepository.findAll()
  }

  override fun getTrackCounts(): Map<String, Int> {
    currentUserResolver.userId() ?: return emptyMap()
    return playlistRepository.findTrackCounts()
  }

  override fun getArtistStats(): Map<String, ArtistStats> {
    currentUserResolver.userId() ?: return emptyMap()
    val artistIdsByPlaylist = playlistRepository.findDistinctArtistIds()
    val allArtistIds = artistIdsByPlaylist.values.flatten().toSet()
    val existingArtistIds = appArtistRepository.findByArtistIds(allArtistIds).map { it.id }.toSet()
    return artistIdsByPlaylist.mapValues { (_, artistIds) ->
      ArtistStats(
        total = artistIds.size,
        missingFromCatalog = artistIds.count { it !in existingArtistIds },
      )
    }
  }

  override fun getMissingArtists(playlistId: String): List<MissingArtist> {
    currentUserResolver.userId() ?: return emptyList()
    val playlist = playlistRepository.findByPlaylistId(playlistId) ?: return emptyList()
    // Only the main (first) artist per track is checked against the catalog, matching syncPlaylistData()'s
    // catalog sync which only ever adds a track's primary artist - featured/additional artists are never added
    // and would otherwise always be reported as missing.
    val artistIds = playlist.tracks.mapNotNull { it.artistIds.firstOrNull() }.toSet()
    val existingArtistIds = appArtistRepository.findByArtistIds(artistIds).map { it.id }.toSet()
    val missingArtistIds = artistIds.filterNot { it in existingArtistIds }
    if (missingArtistIds.isEmpty()) return emptyList()
    val accessToken = spotifyAccessToken.getValidAccessToken()
    // Resolved in as few batched Spotify calls as possible (up to 50 ids per request), rather than one
    // throttled request per missing artist, which made this endpoint take minutes for larger playlists.
    val resolvedNamesById = spotifyCatalog.getArtists(accessToken, missingArtistIds.map { it.value })
      .getOrElse { emptyList() }
      .associate { it.id to it.artistName }
    return missingArtistIds
      .map { artistId -> MissingArtist(id = artistId.value, name = resolvedNamesById[artistId]) }
      .sortedBy { it.name ?: it.id }
  }

  override fun enqueueUpdates() {
    currentUserResolver.userId() ?: return
    outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistInfo())
    lastSyncJobSuccessTimestamp.set(Clock.System.now().toEpochMilliseconds() / MILLIS_PER_SECOND)
  }

  override fun syncPlaylists(): Either<DomainError, Unit> {
    val userId = currentUserResolver.userId() ?: return Unit.right()
    val accessToken = spotifyAccessToken.getValidAccessToken()
    val playlistsResult = spotifyPlaylist.getPlaylists(accessToken)
    if (playlistsResult is Either.Left && playlistsResult.value !is SpotifyRateLimitError) {
      syncNotification.notifySyncFailed(playlistsResult.value.code)
    }
    return playlistsResult.map { spotifyPlaylists ->
      val now = Clock.System.now()
      val existingById = playlistRepository.findAll().associateBy { it.spotifyPlaylistId }
      val updatedPlaylists = spotifyPlaylists.filter { it.ownerId == userId.value }.map { item ->
        val existing = existingById[item.id]
        PlaylistInfo(
          spotifyPlaylistId = item.id,
          snapshotId = item.snapshotId,
          lastSnapshotIdSyncTime = if (existing == null || existing.snapshotId != item.snapshotId) now else existing.lastSnapshotIdSyncTime,
          name = item.name,
          syncStatus = existing?.syncStatus ?: PlaylistSyncStatus.PASSIVE,
          type = existing?.type,
        )
      }
      playlistRepository.replaceAll(updatedPlaylists)
      if (updatedPlaylists.size != existingById.size) {
        dashboardRefresh.notifyUserPlaylistMetadata()
      }
      updatedPlaylists
        .filter { it.syncStatus == PlaylistSyncStatus.ACTIVE }
        .filter { playlist ->
          val existing = existingById[playlist.spotifyPlaylistId]
          existing == null ||
            existing.snapshotId != playlist.snapshotId ||
            playlistRepository.findByPlaylistId(playlist.spotifyPlaylistId) == null
        }
        .forEach { playlist ->
          outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(playlist.spotifyPlaylistId))
        }
    }
  }

  override fun syncPlaylistData(playlistId: String, nextUrl: String?, snapshotId: String?): Either<DomainError, Unit> {
    currentUserResolver.userId() ?: return Unit.right()
    val accessToken = spotifyAccessToken.getValidAccessToken()
    val isFirstPage = nextUrl == null
    return spotifyPlaylist.getPlaylistTracksPage(accessToken, playlistId, nextUrl).map { page ->
      if (snapshotId != null && page.snapshotId != snapshotId) {
        logger.warn { "Snapshot changed for playlist $playlistId (expected $snapshotId, got ${page.snapshotId}), restarting sync from first page" }
        outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(playlistId))
        return@map
      }
      if (isFirstPage) {
        playlistRepository.save(Playlist(playlistId, page.tracks))
      } else {
        playlistRepository.appendTracks(playlistId, page.tracks)
      }

      val catalogRequests = page.tracks.map {
        CatalogSyncRequest(it.trackId.value, listOfNotNull(it.artistIds.firstOrNull()?.value), SyncCause.Playlist(playlistId, it.trackId.value))
      }
      syncController.syncForTracks(catalogRequests)
      catalogPort.promoteAssumptionArtistsFoundOnPlaylist(catalogRequests.flatMap { it.artistIds }.toSet())

      if (page.nextUrl != null) {
        outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(playlistId, page.nextUrl, page.snapshotId))
      } else {
        logger.info { "Completed all pages for playlist $playlistId" }
        playlistRepository.updateLastSyncTime(playlistId, Clock.System.now())
        outboxPort.enqueue(DomainOutboxEvent.RunPlaylistChecks(playlistId))
      }
    }
  }

  override fun updateSyncStatus(playlistId: String, syncStatus: PlaylistSyncStatus): Either<DomainError, Unit> {
    currentUserResolver.userId() ?: return PlaylistSyncError.PLAYLIST_NOT_FOUND.left()
    val playlists = playlistRepository.findAll()
    val playlist = playlists.find { it.spotifyPlaylistId == playlistId }
      ?: return PlaylistSyncError.PLAYLIST_NOT_FOUND.left()
    val updatedPlaylists = playlists.map {
      if (it.spotifyPlaylistId == playlistId) {
        val newType = when {
          syncStatus == PlaylistSyncStatus.PASSIVE -> null
          it.type != null -> it.type
          it.name.equals("all", ignoreCase = true) -> PlaylistType.ALL
          it.name.matches(YEAR_NAME_REGEX) -> PlaylistType.YEAR
          it.name.equals(PlaylistType.SINGULARITY_PLAYLIST_NAME, ignoreCase = true) -> PlaylistType.SINGULARITY
          else -> PlaylistType.UNKNOWN
        }
        it.copy(syncStatus = syncStatus, type = newType)
      } else {
        it
      }
    }
    logger.info { "Updated sync status for playlist '${playlist.name}' ($playlistId) to $syncStatus" }
    playlistRepository.replaceAll(updatedPlaylists)
    dashboardRefresh.notifyUserPlaylistMetadata()
    if (syncStatus == PlaylistSyncStatus.PASSIVE) {
      logger.info { "Deleting checks for deactivated playlist '${playlist.name}' ($playlistId)" }
      playlistCheckRepository.deleteByPlaylistId(playlistId)
    } else if (syncStatus == PlaylistSyncStatus.ACTIVE) {
      logger.info { "Enqueueing SyncPlaylistData for activated playlist '${playlist.name}' ($playlistId)" }
      outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(playlistId))
    }
    return Unit.right()
  }

  override fun updatePlaylistType(playlistId: String, type: PlaylistType): Either<DomainError, Unit> {
    currentUserResolver.userId() ?: return PlaylistSyncError.PLAYLIST_NOT_FOUND.left()
    val validationError = validatePlaylistTypeUpdate(playlistId, type)
    if (validationError != null) return validationError.left()
    val playlists = playlistRepository.findAll()
    val updatedPlaylists = playlists.map {
      if (it.spotifyPlaylistId == playlistId) it.copy(type = type) else it
    }
    val playlistName = playlists.find { it.spotifyPlaylistId == playlistId }?.name ?: playlistId
    logger.info { "Updated type for playlist '$playlistName' ($playlistId) to $type" }
    playlistRepository.replaceAll(updatedPlaylists)
    dashboardRefresh.notifyUserPlaylistMetadata()
    return Unit.right()
  }

  private fun validatePlaylistTypeUpdate(playlistId: String, type: PlaylistType): PlaylistSyncError? {
    val playlists = playlistRepository.findAll()
    val playlist = playlists.find { it.spotifyPlaylistId == playlistId }
    return when {
      playlist == null -> PlaylistSyncError.PLAYLIST_NOT_FOUND
      playlist.syncStatus != PlaylistSyncStatus.ACTIVE -> PlaylistSyncError.PLAYLIST_NOT_ACTIVE
      type == PlaylistType.ALL && playlists.any { it.type == PlaylistType.ALL && it.spotifyPlaylistId != playlistId } ->
        PlaylistSyncError.PLAYLIST_TYPE_CONFLICT
      type == PlaylistType.SINGULARITY && playlists.any { it.type == PlaylistType.SINGULARITY && it.spotifyPlaylistId != playlistId } ->
        PlaylistSyncError.PLAYLIST_TYPE_CONFLICT
      else -> null
    }
  }

  override fun enqueueSyncPlaylistData(playlistId: String): Either<DomainError, Unit> {
    currentUserResolver.userId() ?: return PlaylistSyncError.PLAYLIST_NOT_FOUND.left()
    val playlists = playlistRepository.findAll()
    val playlist = playlists.find { it.spotifyPlaylistId == playlistId }
      ?: return PlaylistSyncError.PLAYLIST_NOT_FOUND.left()
    return if (playlist.syncStatus != PlaylistSyncStatus.ACTIVE) {
      PlaylistSyncError.PLAYLIST_SYNC_INACTIVE.left()
    } else {
      logger.info { "Enqueueing SyncPlaylistData for playlist '${playlist.name}' ($playlistId)" }
      outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(playlistId))
      Unit.right()
    }
  }

  override fun handle(event: DomainOutboxEvent.SyncPlaylistInfo): Either<DomainError, Unit> =
    syncPlaylists()

  override fun handle(event: DomainOutboxEvent.SyncPlaylistData): Either<DomainError, Unit> =
    syncPlaylistData(event.playlistId, event.nextUrl, event.snapshotId)

  companion object : KLogging() {
    private val YEAR_NAME_REGEX = Regex("\\d{4}")
    private const val MILLIS_PER_SECOND = 1_000L
  }
}
