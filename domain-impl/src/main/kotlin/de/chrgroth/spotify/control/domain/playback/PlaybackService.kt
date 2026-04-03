package de.chrgroth.spotify.control.domain.playback

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.playback.AppPlaybackItem
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistPlaybackProcessingStatus
import de.chrgroth.spotify.control.domain.model.playback.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPartialPlayedItem
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogPort
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.CurrentlyPlayingRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackStatePort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPartialPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.catalog.SyncController
import de.chrgroth.spotify.control.domain.catalog.CatalogSyncRequest
import de.chrgroth.spotify.control.domain.port.out.playback.SpotifyPlaybackPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class PlaybackService(
  private val userRepository: UserRepositoryPort,
  private val spotifyAccessToken: SpotifyAccessTokenPort,
  private val spotifyPlayback: SpotifyPlaybackPort,
  private val currentlyPlayingRepository: CurrentlyPlayingRepositoryPort,
  private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort,
  private val recentlyPartialPlayedRepository: RecentlyPartialPlayedRepositoryPort,
  private val appPlaybackRepository: AppPlaybackRepositoryPort,
  private val appArtistRepository: AppArtistRepositoryPort,
  private val syncController: SyncController,
  private val outboxPort: OutboxPort,
  private val dashboardRefresh: DashboardRefreshPort,
  private val playbackState: PlaybackStatePort,
  private val catalog: CatalogPort,
  private val playlistRepository: PlaylistRepositoryPort,
  @ConfigProperty(name = "app.playback.minimum-progress-seconds", defaultValue = "25")
  minimumProgressSeconds: Long,
) : PlaybackPort {

  private val minimumProgressMs = minimumProgressSeconds * MS_PER_SECOND

  // --- Currently Playing ---

  override fun enqueueFetchCurrentlyPlaying() {
    val users = userRepository.findAll()
    logger.info { "Scheduling currently playing fetch for ${users.size} user(s)" }
    users.forEach { user ->
      outboxPort.enqueue(DomainOutboxEvent.FetchCurrentlyPlaying(user.spotifyUserId))
    }
  }

  override fun fetchCurrentlyPlaying(userId: UserId): Either<DomainError, Unit> {
    val accessToken = spotifyAccessToken.getValidAccessToken(userId)
    return spotifyPlayback.getCurrentlyPlaying(userId, accessToken).flatMap { item ->
      if (item != null && item.isPlaying) {
        playbackState.onPlaybackDetected()
      }
      if (item != null) {
        if (currentlyPlayingRepository.existsByUserAndTrackAndObservedMinute(item)) {
          logger.info { "Updating currently playing item for user: ${userId.value}, track: ${item.trackId}" }
          currentlyPlayingRepository.updateProgressByUserAndTrackAndObservedMinute(item)
        } else {
          logger.info { "Persisting currently playing item for user: ${userId.value}, track: ${item.trackId}" }
          currentlyPlayingRepository.save(item)
        }
        dashboardRefresh.notifyUserPlaybackData(userId)
      }
      Unit.right()
    }
  }

  // --- Recently Played ---

  override fun enqueueFetchRecentlyPlayed() {
    val users = userRepository.findAll()
    logger.info { "Scheduling recently played fetch for ${users.size} user(s)" }
    users.forEach { user ->
      outboxPort.enqueue(DomainOutboxEvent.FetchRecentlyPlayed(user.spotifyUserId))
    }
  }

  override fun fetchRecentlyPlayed(userId: UserId): Either<DomainError, Unit> {
    val accessToken = spotifyAccessToken.getValidAccessToken(userId)
    val after = recentlyPlayedRepository.findMostRecentPlayedAt(userId)
    return spotifyPlayback.getRecentlyPlayed(userId, accessToken, after).flatMap { tracks ->
      val playedAts = tracks.map { it.playedAt }.toSet()
      val existingPlayedAts = recentlyPlayedRepository.findExistingPlayedAts(userId, playedAts)
      val newItems = tracks.filter { it.playedAt !in existingPlayedAts }
      if (newItems.isNotEmpty()) {
        logger.info { "Persisting ${newItems.size} new recently played items for user: ${userId.value}" }
        recentlyPlayedRepository.saveAll(newItems)
        deduplicateWithPartialPlays(userId, newItems)
      }
      val computedCount = convertPartialPlays(userId, tracks.map { it.trackId }.toSet())
      if (newItems.isNotEmpty() || computedCount > 0) {
        dashboardRefresh.notifyUserPlaybackData(userId)
        outboxPort.enqueue(DomainOutboxEvent.AppendPlaybackData(userId))
      }
      Unit.right()
    }
  }

  private fun deduplicateWithPartialPlays(userId: UserId, newRecentlyPlayedItems: List<RecentlyPlayedItem>) {
    val itemsWithDuration = newRecentlyPlayedItems.filter { (it.durationSeconds ?: 0) > 0 }
    if (itemsWithDuration.isEmpty()) return

    val trackIds = itemsWithDuration.map { it.trackId }.toSet()
    val partialPlays = recentlyPartialPlayedRepository.findByUserIdAndTrackIds(userId, trackIds)
    if (partialPlays.isEmpty()) return

    val duplicatePlayedAts = mutableSetOf<Instant>()
    for (recentlyPlayed in itemsWithDuration) {
      val duration = recentlyPlayed.durationSeconds ?: continue
      val startTime = recentlyPlayed.playedAt - duration.seconds
      for (partial in partialPlays.filter { it.trackId == recentlyPlayed.trackId }) {
        val partialStartTime = partial.playedAt - partial.playedSeconds.seconds
        val startTimeDifferenceSeconds = (startTime - partialStartTime).absoluteValue.inWholeSeconds
        if (startTimeDifferenceSeconds <= PARTIAL_DUPLICATE_TOLERANCE_SECONDS) {
          duplicatePlayedAts.add(partial.playedAt)
        }
      }
    }

    if (duplicatePlayedAts.isNotEmpty()) {
      logger.info { "Removing ${duplicatePlayedAts.size} duplicate partial play(s) superseded by recently played for user: ${userId.value}" }
      recentlyPartialPlayedRepository.deleteByPlayedAts(userId, duplicatePlayedAts)
      appPlaybackRepository.deleteByUserAndPlayedAts(userId, duplicatePlayedAts)
    }
  }

  private fun convertPartialPlays(userId: UserId, completedTrackIds: Set<TrackId>): Int {
    val sortedItems = currentlyPlayingRepository.findByUserId(userId).sortedBy { it.observedAt }

    // The single latest item is protected — it may still be active
    val latestItem = sortedItems.lastOrNull()
    val itemsToProcess = if (latestItem != null) sortedItems.dropLast(1) else emptyList()

    val convertibleItems = itemsToProcess.filter { item ->
      item.trackId !in completedTrackIds && item.progressMs > minimumProgressMs
    }

    val newComputedCount = if (convertibleItems.isNotEmpty()) {
      val partialItems = convertibleItems.map { item ->
        val playedMs = minOf(item.progressMs, item.durationMs)
        RecentlyPartialPlayedItem(
          spotifyUserId = userId,
          trackId = item.trackId,
          trackName = item.trackName,
          artistIds = item.artistIds,
          artistNames = item.artistNames,
          playedAt = item.observedAt,
          playedSeconds = playedMs / MS_PER_SECOND,
          albumId = item.albumId,
        )
      }
      val existingPlayedAts = recentlyPartialPlayedRepository.findExistingPlayedAts(userId, partialItems.map { it.playedAt }.toSet())
      val newPartial = partialItems.filter { it.playedAt !in existingPlayedAts }
      if (newPartial.isNotEmpty()) {
        logger.info { "Persisting ${newPartial.size} recently partial played items for user: ${userId.value}" }
        recentlyPartialPlayedRepository.saveAll(newPartial)
      }
      newPartial.size
    } else {
      0
    }

    // Delete completed tracks and all processed items (converted or skipped below threshold),
    // but don't delete the latest item's trackId as it may still be active
    val allProcessedTrackIds = itemsToProcess.map { it.trackId }.filter { it != latestItem?.trackId }.toSet()
    currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, (completedTrackIds + allProcessedTrackIds).map { it.value }.toSet())
    return newComputedCount
  }

  // --- Playback Data ---

  override fun enqueueRebuildPlaybackData(userId: UserId) {
    logger.info { "Enqueuing playback data rebuild for user: ${userId.value}" }
    outboxPort.enqueue(DomainOutboxEvent.RebuildPlaybackData(userId))
  }

  override fun rebuildPlaybackData(userId: UserId) {
    logger.info { "Rebuilding playback data for user: ${userId.value}" }
    appPlaybackRepository.deleteAllByUserId(userId)
    appendPlaybackData(userId)
  }

  override fun appendPlaybackData(userId: UserId) {
    logger.info { "Appending playback data for user: ${userId.value}" }
    val since = appPlaybackRepository.findMostRecentPlayedAt(userId)
    val recentlyPlayed = recentlyPlayedRepository.findSince(userId, since)
    val partialPlayed = recentlyPartialPlayedRepository.findSince(userId, since)

    val inactiveArtistIds = appArtistRepository.findByPlaybackProcessingStatus(ArtistPlaybackProcessingStatus.INACTIVE)
      .map { it.id.value }
      .toSet()

    val filteredRecentlyPlayed = recentlyPlayed.filter { it.artistIds.firstOrNull()?.value !in inactiveArtistIds }
    val filteredPartialPlayed = partialPlayed.filter { it.artistIds.firstOrNull()?.value !in inactiveArtistIds }

    val allPlaybackItems = buildPlaybackItems(filteredRecentlyPlayed, filteredPartialPlayed)
    if (allPlaybackItems.isEmpty()) {
      logger.info { "No new playback items to append for user: ${userId.value}" }
      return
    }

    val existingPlayedAts = appPlaybackRepository.findExistingPlayedAts(
      userId = userId,
      playedAts = allPlaybackItems.map { it.playedAt }.toSet(),
    )
    val newPlaybackItems = allPlaybackItems.filter { it.playedAt !in existingPlayedAts }
    if (newPlaybackItems.isEmpty()) {
      logger.info { "All playback items already exist for user: ${userId.value}" }
      return
    }

    logger.info { "Persisting ${newPlaybackItems.size} new app_playback items for user: ${userId.value}" }
    appPlaybackRepository.saveAll(newPlaybackItems)

    val catalogRequests = (
      filteredRecentlyPlayed.map { CatalogSyncRequest(it.trackId.value, it.albumId?.value, it.artistIds.map { a -> a.value }) } +
        filteredPartialPlayed.map { CatalogSyncRequest(it.trackId.value, it.albumId?.value, it.artistIds.map { a -> a.value }) }
    ).distinctBy { it.trackId }
    syncController.syncForTracks(catalogRequests, userId)
  }

  private fun buildPlaybackItems(
    recentlyPlayed: List<RecentlyPlayedItem>,
    partialPlayed: List<RecentlyPartialPlayedItem>,
  ) = recentlyPlayed.map { item ->
    AppPlaybackItem(
      userId = item.spotifyUserId,
      playedAt = item.playedAt,
      trackId = item.trackId.value,
      secondsPlayed = item.durationSeconds ?: 0L,
    )
  } + partialPlayed.map { item ->
    AppPlaybackItem(
      userId = item.spotifyUserId,
      playedAt = item.playedAt,
      trackId = item.trackId.value,
      secondsPlayed = item.playedSeconds,
    )
  }

  // --- Artist Playback Sync ---

  override fun syncArtistPlaybackFromPlaylists(userId: UserId) {
    val activePlaylistArtistIds = playlistRepository.findArtistIdsInActivePlaylists()
    logger.info { "Found ${activePlaylistArtistIds.size} artist(s) in active playlists" }

    ArtistPlaybackProcessingStatus.entries.forEach { currentStatus ->
      val artists = appArtistRepository.findByPlaybackProcessingStatus(currentStatus)
      artists.forEach { artist ->
        val inActivePlaylist = artist.id.value in activePlaylistArtistIds
        val newStatus = when (currentStatus) {
          ArtistPlaybackProcessingStatus.UNDECIDED ->
            if (inActivePlaylist) ArtistPlaybackProcessingStatus.ACTIVE else ArtistPlaybackProcessingStatus.INACTIVE
          ArtistPlaybackProcessingStatus.ACTIVE ->
            if (!inActivePlaylist) ArtistPlaybackProcessingStatus.INACTIVE else null
          ArtistPlaybackProcessingStatus.INACTIVE ->
            if (inActivePlaylist) ArtistPlaybackProcessingStatus.ACTIVE else null
        }
        if (newStatus != null) {
          logger.info { "Sync from playlists: updating artist ${artist.id.value} from $currentStatus to $newStatus" }
          catalog.updateArtistPlaybackProcessingStatus(artist.id.value, newStatus, userId)
        }
      }
    }
  }

  // --- Outbox Handlers ---

  override fun handle(event: DomainOutboxEvent.FetchCurrentlyPlaying): Either<DomainError, Unit> =
    fetchCurrentlyPlaying(event.userId)

  override fun handle(event: DomainOutboxEvent.FetchRecentlyPlayed): Either<DomainError, Unit> =
    fetchRecentlyPlayed(event.userId)

  override fun handle(event: DomainOutboxEvent.RebuildPlaybackData): Either<DomainError, Unit> {
    rebuildPlaybackData(event.userId)
    return Unit.right()
  }

  override fun handle(event: DomainOutboxEvent.AppendPlaybackData): Either<DomainError, Unit> {
    appendPlaybackData(event.userId)
    return Unit.right()
  }

  companion object : KLogging() {
    private const val MS_PER_SECOND = 1_000L
    private const val PARTIAL_DUPLICATE_TOLERANCE_SECONDS = 30L
  }
}
