package de.chrgroth.spotify.control.domain.playlist

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.PlaylistSyncError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistSyncStatus
import de.chrgroth.spotify.control.domain.model.playlist.ArtistStats
import de.chrgroth.spotify.control.domain.model.playlist.MissingArtist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSettingsEntry
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSettingsView
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
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistSyncNotificationPort
import de.chrgroth.spotify.control.domain.port.out.readmodel.PlaylistSettingsViewRepositoryPort
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
  private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort,
  private val spotifyCatalog: SpotifyCatalogPort,
  private val meterRegistry: MeterRegistry,
  private val syncNotification: PlaylistSyncNotificationPort,
  private val playlistSettingsViewRepository: PlaylistSettingsViewRepositoryPort,
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

  private fun getTrackCounts(): Map<String, Int> {
    currentUserResolver.userId() ?: return emptyMap()
    return playlistRepository.findTrackCounts()
  }

  private fun getArtistStats(playlists: List<PlaylistInfo>): Pair<Map<String, ArtistStats>, Set<ArtistId>> {
    currentUserResolver.userId() ?: return emptyMap<String, ArtistStats>() to emptySet()
    val artistIdsByPlaylist = playlistRepository.findDistinctArtistIds()
    val allArtistIds = artistIdsByPlaylist.values.flatten().toSet()
    val existingArtistIds = appArtistRepository.findByArtistIds(allArtistIds).map { it.id }.toSet()
    val activePlaylistIds = playlists.filter { it.syncStatus == PlaylistSyncStatus.ACTIVE }.map { it.spotifyPlaylistId }.toSet()
    val artistIdsOnActivePlaylists = artistIdsByPlaylist.filterKeys { it in activePlaylistIds }.values.flatten().toSet()
    val stats = artistIdsByPlaylist.mapValues { (_, artistIds) ->
      val missingArtistIds = artistIds.filterNot { it in existingArtistIds }
      ArtistStats(
        total = artistIds.size,
        missingFromCatalog = missingArtistIds.size,
        missingArtistIds = missingArtistIds,
      )
    }
    return stats to artistIdsOnActivePlaylists
  }

  override fun getPlaylistSettingsView(): PlaylistSettingsView =
    playlistSettingsViewRepository.find() ?: EMPTY_SETTINGS_VIEW

  override fun rebuildPlaylistSettingsView() {
    playlistSettingsViewRepository.save(buildPlaylistSettingsView())
  }

  private fun buildPlaylistSettingsView(): PlaylistSettingsView {
    val playlists = getPlaylists()
    val trackCounts = getTrackCounts()
    val (artistStats, artistIdsOnActivePlaylists) = getArtistStats(playlists)
    val missingArtistIds = artistStats.values.flatMap { it.missingArtistIds }.toSet()
    val previouslyResolvedNames = if (missingArtistIds.isEmpty()) {
      emptyMap()
    } else {
      playlistSettingsViewRepository.find()
        ?.entries
        ?.flatMap { it.missingArtists }
        ?.mapNotNull { artist -> artist.name?.let { ArtistId(artist.id) to it } }
        ?.toMap()
        .orEmpty()
    }
    // Names resolved by a previous rebuild are kept even if this Spotify call fails or is rate-limited, and
    // already-resolved artists are not re-requested, so a transient failure never regresses the read model
    // back to unresolved names and repeated rebuilds don't keep re-fetching the same artists.
    val resolvedNamesById = previouslyResolvedNames +
      resolveMissingArtistNames(missingArtistIds - previouslyResolvedNames.keys, artistIdsOnActivePlaylists)
    val entries = playlists.map { playlistInfo ->
      val stats = artistStats[playlistInfo.spotifyPlaylistId]
      PlaylistSettingsEntry(
        playlist = playlistInfo,
        numberOfTracks = trackCounts[playlistInfo.spotifyPlaylistId],
        numberOfArtists = stats?.total,
        numberOfMissingArtists = stats?.missingFromCatalog,
        missingArtists = stats?.missingArtistIds
          ?.map { artistId -> MissingArtist(id = artistId.value, name = resolvedNamesById[artistId]) }
          ?.sortedBy { it.name ?: it.id }
          ?: emptyList(),
      )
    }
    return PlaylistSettingsView(entries)
  }

  private fun resolveMissingArtistNames(missingArtistIds: Set<ArtistId>, artistIdsOnActivePlaylists: Set<ArtistId>): Map<ArtistId, String?> {
    if (missingArtistIds.isEmpty()) return emptyMap()
    // Artist ids seen in local playback history already carry a name, so those are resolved without any Spotify
    // request; only the ids still unresolved after that are worth spending a Spotify call on.
    val locallyResolvedNames = recentlyPlayedRepository.findArtistNamesByIds(missingArtistIds)
    val remainingArtistIds = missingArtistIds - locallyResolvedNames.keys
    if (remainingArtistIds.isEmpty()) return locallyResolvedNames
    val accessToken = try {
      spotifyAccessToken.getValidAccessToken()
    } catch (e: Exception) {
      logger.warn(e) { "Failed to resolve missing artist names, playlist settings view will show artist ids only" }
      return locallyResolvedNames
    }
    // Resolved one throttled single-artist call at a time (GET /v1/artists/{id}) instead of the bulk
    // GET /v1/artists endpoint, which reliably returns 403 for this app. Every artist resolved this way is
    // persisted into the local catalog immediately (same SYNC/SHALLOW_ASSUMPTION discovery rule as the regular
    // catalog sync path), so it is found locally without another Spotify request on every future rebuild.
    val spotifyResolvedNames = remainingArtistIds.mapNotNull { artistId ->
      spotifyCatalog.getArtist(accessToken, artistId.value)
        .onLeft { logger.warn { "Failed to resolve missing artist name for ${artistId.value}: ${it.code}" } }
        .getOrElse { null }
        ?.takeIf { it.artistName.isNotBlank() }
        ?.also { storeNewlyResolvedArtist(it, fromPlaylist = artistId in artistIdsOnActivePlaylists) }
        ?.let { artistId to it.artistName }
    }.toMap()
    return locallyResolvedNames + spotifyResolvedNames
  }

  private fun storeNewlyResolvedArtist(artist: AppArtist, fromPlaylist: Boolean) {
    val discoveryStatus = if (fromPlaylist) ArtistSyncStatus.SYNC else ArtistSyncStatus.SHALLOW_ASSUMPTION
    appArtistRepository.upsertAll(listOf(artist.copy(syncStatus = discoveryStatus)))
    if (discoveryStatus == ArtistSyncStatus.SYNC) {
      logger.info { "Newly discovered artist '${artist.artistName}' (${artist.id.value}) found on synced playlist, set to ${ArtistSyncStatus.SYNC}" }
      outboxPort.enqueue(DomainOutboxEvent.SyncArtistAlbums(artist.id.value))
    }
  }

  override fun getMissingArtists(playlistId: String): List<MissingArtist> {
    currentUserResolver.userId() ?: return emptyList()
    return playlistSettingsViewRepository.find()
      ?.entries
      ?.find { it.playlist.spotifyPlaylistId == playlistId }
      ?.missingArtists
      .orEmpty()
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
      outboxPort.enqueue(DomainOutboxEvent.RebuildPlaylistSettingsView())
      outboxPort.enqueue(DomainOutboxEvent.RebuildDashboardReadModel())
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
        CatalogSyncRequest(it.trackId.value, listOf(it.mainArtistId.value), SyncCause.Playlist(playlistId, it.trackId.value))
      }
      syncController.syncForTracks(catalogRequests)
      catalogPort.promoteAssumptionArtistsFoundOnPlaylist(catalogRequests.flatMap { it.artistIds }.toSet())

      if (page.nextUrl != null) {
        outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(playlistId, page.nextUrl, page.snapshotId))
      } else {
        logger.info { "Completed all pages for playlist $playlistId" }
        playlistRepository.updateLastSyncTime(playlistId, Clock.System.now())
        outboxPort.enqueue(DomainOutboxEvent.RunPlaylistChecks(playlistId))
        outboxPort.enqueue(DomainOutboxEvent.RebuildPlaylistSettingsView())
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
    outboxPort.enqueue(DomainOutboxEvent.RebuildPlaylistSettingsView())
    outboxPort.enqueue(DomainOutboxEvent.RebuildDashboardReadModel())
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
    outboxPort.enqueue(DomainOutboxEvent.RebuildPlaylistSettingsView())
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

  override fun handle(event: DomainOutboxEvent.RebuildPlaylistSettingsView): Either<DomainError, Unit> {
    currentUserResolver.userId() ?: return Unit.right()
    rebuildPlaylistSettingsView()
    return Unit.right()
  }

  companion object : KLogging() {
    private val YEAR_NAME_REGEX = Regex("\\d{4}")
    private const val MILLIS_PER_SECOND = 1_000L
    private val EMPTY_SETTINGS_VIEW = PlaylistSettingsView(emptyList())
  }
}
