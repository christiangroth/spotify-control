package de.chrgroth.spotify.control.domain.playback

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.playback.AppPlaybackItem
import de.chrgroth.spotify.control.domain.model.playback.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPartialPlayedItem
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.catalog.SyncCause
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackPort
import de.chrgroth.spotify.control.domain.port.out.playback.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.CurrentlyPlayingRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackStatePort
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPartialPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.catalog.SyncController
import de.chrgroth.spotify.control.domain.catalog.CatalogSyncRequest
import de.chrgroth.spotify.control.domain.port.out.playback.SpotifyPlaybackPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import jakarta.enterprise.context.ApplicationScoped
import java.time.ZoneOffset
import java.time.LocalDate as JLocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlinx.datetime.toKotlinLocalDate
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
  private val syncController: SyncController,
  private val outboxPort: OutboxPort,
  private val dashboardRefresh: DashboardRefreshPort,
  private val playbackState: PlaybackStatePort,
  private val meterRegistry: MeterRegistry,
  @ConfigProperty(name = "app.playback.minimum-progress-seconds", defaultValue = "25")
  minimumProgressSeconds: Long,
) : PlaybackPort {

  private val minimumProgressMs = minimumProgressSeconds * MS_PER_SECOND
  private val lastFetchSuccessTimestamps = ConcurrentHashMap<String, AtomicLong>()

  // --- Combined Playback Detection ---

  override fun enqueueFetchPlaybackData() {
    val users = userRepository.findAll()
    users.forEach { user ->
      outboxPort.enqueue(DomainOutboxEvent.FetchPlaybackData(user.spotifyUserId))
    }
  }

  override fun fetchPlaybackData(userId: UserId): Either<DomainError, Unit> {
    val currentlyPlayingResult = fetchCurrentlyPlaying(userId)
    val recentlyPlayedResult = fetchRecentlyPlayed(userId)
    return currentlyPlayingResult.flatMap { recentlyPlayedResult }
  }

  // --- Currently Playing ---

  internal fun fetchCurrentlyPlaying(userId: UserId): Either<DomainError, Unit> {
    val accessToken = spotifyAccessToken.getValidAccessToken(userId)
    return spotifyPlayback.getCurrentlyPlaying(userId, accessToken).flatMap { item ->
      if (item != null && item.isPlaying) {
        playbackState.onPlaybackDetected()
      }
      val orphanedItemsConverted = if (item != null) {
        val existing = currentlyPlayingRepository.findMostRecentByUserAndTrack(userId, item.trackId)
        if (existing != null && !isTrackRestart(item, existing)) {
          currentlyPlayingRepository.updateProgress(item.copy(startTime = existing.startTime))
        } else {
          if (existing != null) {
            currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, setOf(item.trackId.value))
          }
          currentlyPlayingRepository.save(item)
        }
        convertAndDeleteOrphanedItems(userId, item.trackId)
      } else {
        convertAndDeleteOrphanedItems(userId, null)
      }
      if (orphanedItemsConverted) {
        dashboardRefresh.notifyUserPlaybackData(userId)
      }
      recordFetchSuccess(userId, "currently_playing")
      Unit.right()
    }
  }

  private fun isTrackRestart(newItem: CurrentlyPlayingItem, existingItem: CurrentlyPlayingItem): Boolean =
    newItem.progressMs < RESTART_THRESHOLD_MS && existingItem.progressMs > minimumProgressMs

  private fun convertAndDeleteOrphanedItems(userId: UserId, currentTrackId: TrackId?): Boolean {
    val orphanedItems = currentlyPlayingRepository.findByUserId(userId)
      .let { items -> if (currentTrackId != null) items.filter { it.trackId != currentTrackId } else items }
    if (orphanedItems.isEmpty()) return false

    val convertibleItems = orphanedItems.filter { it.progressMs > minimumProgressMs }
    val newPartialSaved = if (convertibleItems.isNotEmpty()) {
      val partialItems = convertibleItems.map { item ->
        val playedMs = minOf(item.progressMs, item.durationMs)
        RecentlyPartialPlayedItem(
          spotifyUserId = userId,
          trackId = item.trackId,
          trackName = item.trackName,
          artistIds = item.artistIds,
          artistNames = item.artistNames,
          playedAt = item.observedAt,
          startTime = item.startTime,
          playedSeconds = playedMs / MS_PER_SECOND,
          albumId = item.albumId,
        )
      }
      val existingPlayedAts = recentlyPartialPlayedRepository.findExistingPlayedAts(userId, partialItems.map { it.playedAt }.toSet())
      val newPartial = partialItems.filter { it.playedAt !in existingPlayedAts }
      if (newPartial.isNotEmpty()) {
        recentlyPartialPlayedRepository.saveAll(newPartial)
      }
      newPartial.isNotEmpty()
    } else {
      false
    }

    val orphanedTrackIds = orphanedItems.map { it.trackId.value }.toSet()
    currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, orphanedTrackIds)
    return newPartialSaved
  }

  // --- Recently Played ---

  internal fun fetchRecentlyPlayed(userId: UserId): Either<DomainError, Unit> {
    val accessToken = spotifyAccessToken.getValidAccessToken(userId)
    val after = recentlyPlayedRepository.findMostRecentPlayedAt(userId)
    return spotifyPlayback.getRecentlyPlayed(userId, accessToken, after).flatMap { tracks ->
      val playedAts = tracks.map { it.playedAt }.toSet()
      val existingPlayedAts = recentlyPlayedRepository.findExistingPlayedAts(userId, playedAts)
      val newItems = tracks.filter { it.playedAt !in existingPlayedAts }
      if (newItems.isNotEmpty()) {
        recentlyPlayedRepository.saveAll(newItems)
        deduplicateWithPartialPlays(userId, newItems)
      }
      val computedCount = convertPartialPlays(userId, tracks.map { it.trackId }.toSet())
      if (newItems.isNotEmpty() || computedCount > 0) {
        dashboardRefresh.notifyUserPlaybackData(userId)
        outboxPort.enqueue(DomainOutboxEvent.AppendPlaybackData(userId))
      }
      recordFetchSuccess(userId, "recently_played")
      Unit.right()
    }
  }

  private fun recordFetchSuccess(userId: UserId, operation: String) {
    val key = "${userId.value}:$operation"
    val timestamp = lastFetchSuccessTimestamps.getOrPut(key) {
      AtomicLong().also { atomic ->
        Gauge.builder("app.playback.last_success_timestamp", atomic) { it.get().toDouble() }
          .description("Epoch second timestamp of the last successful playback fetch")
          .tag("userId", userId.value)
          .tag("operation", operation)
          .register(meterRegistry)
      }
    }
    timestamp.set(Clock.System.now().toEpochMilliseconds() / MS_PER_SECOND)
  }

  private fun deduplicateWithPartialPlays(userId: UserId, newRecentlyPlayedItems: List<RecentlyPlayedItem>) {
    val recentlyPlayedWithStartTime = newRecentlyPlayedItems.filter { it.startTime != null }
    if (recentlyPlayedWithStartTime.isEmpty()) return

    val trackIds = recentlyPlayedWithStartTime.map { it.trackId }.toSet()
    val partialPlays = recentlyPartialPlayedRepository.findByUserIdAndTrackIds(userId, trackIds)
    if (partialPlays.isEmpty()) return

    val duplicatePlayedAts = mutableSetOf<Instant>()
    for (recentlyPlayed in recentlyPlayedWithStartTime) {
      val startTime = recentlyPlayed.startTime ?: continue
      for (partial in partialPlays.filter { it.trackId == recentlyPlayed.trackId }) {
        val startTimeDifferenceSeconds = (startTime - partial.startTime).absoluteValue.inWholeSeconds
        if (startTimeDifferenceSeconds <= PARTIAL_DUPLICATE_TOLERANCE_SECONDS) {
          duplicatePlayedAts.add(partial.playedAt)
        }
      }
    }

    if (duplicatePlayedAts.isNotEmpty()) {
      recentlyPartialPlayedRepository.deleteByPlayedAts(userId, duplicatePlayedAts)
      appPlaybackRepository.deleteByUserAndPlayedAts(userId, duplicatePlayedAts)
      duplicatePlayedAts
        .map { instant -> JLocalDate.ofInstant(instant.toJavaInstant(), ZoneOffset.UTC).toKotlinLocalDate() }
        .toSet()
        .forEach { day ->
          outboxPort.enqueue(DomainOutboxEvent.AggregatePlaybackData(userId, AggregationPeriodType.DAY, day))
        }
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
          startTime = item.observedAt - playedMs.milliseconds,
          playedSeconds = playedMs / MS_PER_SECOND,
          albumId = item.albumId,
        )
      }
      val existingPlayedAts = recentlyPartialPlayedRepository.findExistingPlayedAts(userId, partialItems.map { it.playedAt }.toSet())
      val newPartial = partialItems.filter { it.playedAt !in existingPlayedAts }
      if (newPartial.isNotEmpty()) {
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
    logger.info { "Enqueuing playback data rebuild for user: ${userDisplayName(userId)}" }
    outboxPort.enqueue(DomainOutboxEvent.RebuildPlaybackData(userId))
  }

  override fun rebuildPlaybackData(userId: UserId) {
    logger.info { "Rebuilding playback data for user: ${userDisplayName(userId)}" }
    appPlaybackRepository.deleteAllByUserId(userId)
    appendPlaybackData(userId)
  }

  override fun appendPlaybackData(userId: UserId) {
    val since = appPlaybackRepository.findMostRecentPlayedAt(userId)
    val recentlyPlayed = recentlyPlayedRepository.findSince(userId, since)
    val partialPlayed = recentlyPartialPlayedRepository.findSince(userId, since)

    val allPlaybackItems = buildPlaybackItems(recentlyPlayed, partialPlayed)
    if (allPlaybackItems.isEmpty()) return

    val existingPlayedAts = appPlaybackRepository.findExistingPlayedAts(
      userId = userId,
      playedAts = allPlaybackItems.map { it.playedAt }.toSet(),
    )
    val newPlaybackItems = allPlaybackItems.filter { it.playedAt !in existingPlayedAts }
    if (newPlaybackItems.isEmpty()) return

    appPlaybackRepository.saveAll(newPlaybackItems)

    newPlaybackItems
      .map { item -> JLocalDate.ofInstant(item.playedAt.toJavaInstant(), ZoneOffset.UTC).toKotlinLocalDate() }
      .toSet()
      .forEach { day ->
        outboxPort.enqueue(DomainOutboxEvent.AggregatePlaybackData(userId, AggregationPeriodType.DAY, day))
      }

    val catalogRequests = (
      recentlyPlayed.map { CatalogSyncRequest(it.trackId.value, listOfNotNull(it.artistIds.firstOrNull()?.value), SyncCause.Playback(it.trackId.value)) } +
        partialPlayed.map { CatalogSyncRequest(it.trackId.value, listOfNotNull(it.artistIds.firstOrNull()?.value), SyncCause.Playback(it.trackId.value)) }
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

  // --- Outbox Handlers ---

  override fun handle(event: DomainOutboxEvent.FetchPlaybackData): Either<DomainError, Unit> =
    fetchPlaybackData(event.userId)

  override fun handle(event: DomainOutboxEvent.RebuildPlaybackData): Either<DomainError, Unit> {
    rebuildPlaybackData(event.userId)
    return Unit.right()
  }

  override fun handle(event: DomainOutboxEvent.AppendPlaybackData): Either<DomainError, Unit> {
    appendPlaybackData(event.userId)
    return Unit.right()
  }

  private fun userDisplayName(userId: UserId) = userRepository.findById(userId)?.displayName ?: userId.value

  companion object : KLogging() {
    private const val MS_PER_SECOND = 1_000L
    private const val PARTIAL_DUPLICATE_TOLERANCE_SECONDS = 8L
    private const val RESTART_THRESHOLD_MS = 10_000L
  }
}
