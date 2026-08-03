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
import de.chrgroth.spotify.control.domain.user.CurrentUserResolver
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
  private val currentUserResolver: CurrentUserResolver,
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
    currentUserResolver.userId() ?: return
    outboxPort.enqueue(DomainOutboxEvent.FetchPlaybackData())
  }

  override fun fetchPlaybackData(): Either<DomainError, Unit> {
    currentUserResolver.userId() ?: return Unit.right()
    val currentlyPlayingResult = fetchCurrentlyPlaying()
    val recentlyPlayedResult = fetchRecentlyPlayed()
    return currentlyPlayingResult.flatMap { recentlyPlayedResult }
  }

  // --- Currently Playing ---

  internal fun fetchCurrentlyPlaying(): Either<DomainError, Unit> {
    val accessToken = spotifyAccessToken.getValidAccessToken()
    return spotifyPlayback.getCurrentlyPlaying(accessToken).flatMap { item ->
      if (item != null && item.isPlaying) {
        playbackState.onPlaybackDetected()
      }
      val orphanedItemsConverted = if (item != null) {
        val existing = currentlyPlayingRepository.findMostRecentByTrack(item.trackId)
        if (existing != null && !isTrackRestart(item, existing)) {
          currentlyPlayingRepository.updateProgress(item.copy(startTime = existing.startTime))
        } else {
          if (existing != null) {
            currentlyPlayingRepository.deleteByTrackIds(setOf(item.trackId.value))
          }
          currentlyPlayingRepository.save(item)
        }
        convertAndDeleteOrphanedItems(item.trackId)
      } else {
        convertAndDeleteOrphanedItems(null)
      }
      if (orphanedItemsConverted) {
        dashboardRefresh.notifyUserPlaybackData()
      }
      recordFetchSuccess("currently_playing")
      Unit.right()
    }
  }

  private fun isTrackRestart(newItem: CurrentlyPlayingItem, existingItem: CurrentlyPlayingItem): Boolean =
    newItem.progressMs < RESTART_THRESHOLD_MS && existingItem.progressMs > minimumProgressMs

  private fun convertAndDeleteOrphanedItems(currentTrackId: TrackId?): Boolean {
    val orphanedItems = currentlyPlayingRepository.findAll()
      .let { items -> if (currentTrackId != null) items.filter { it.trackId != currentTrackId } else items }
    if (orphanedItems.isEmpty()) return false

    val convertibleItems = orphanedItems.filter { it.progressMs > minimumProgressMs }
    val newPartialSaved = if (convertibleItems.isNotEmpty()) {
      val partialItems = convertibleItems.map { item ->
        val playedMs = minOf(item.progressMs, item.durationMs)
        RecentlyPartialPlayedItem(
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
      val existingPlayedAts = recentlyPartialPlayedRepository.findExistingPlayedAts(partialItems.map { it.playedAt }.toSet())
      val newPartial = partialItems.filter { it.playedAt !in existingPlayedAts }
      if (newPartial.isNotEmpty()) {
        recentlyPartialPlayedRepository.saveAll(newPartial)
        recordEventsIngested("partial_played", newPartial.size)
      }
      newPartial.isNotEmpty()
    } else {
      false
    }

    val orphanedTrackIds = orphanedItems.map { it.trackId.value }.toSet()
    currentlyPlayingRepository.deleteByTrackIds(orphanedTrackIds)
    return newPartialSaved
  }

  // --- Recently Played ---

  internal fun fetchRecentlyPlayed(): Either<DomainError, Unit> {
    val accessToken = spotifyAccessToken.getValidAccessToken()
    val after = recentlyPlayedRepository.findMostRecentPlayedAt()
    return spotifyPlayback.getRecentlyPlayed(accessToken, after).flatMap { tracks ->
      val playedAts = tracks.map { it.playedAt }.toSet()
      val existingPlayedAts = recentlyPlayedRepository.findExistingPlayedAts(playedAts)
      val newItems = tracks.filter { it.playedAt !in existingPlayedAts }
      if (newItems.isNotEmpty()) {
        recentlyPlayedRepository.saveAll(newItems)
        recordEventsIngested("recently_played", newItems.size)
        deduplicateWithPartialPlays(newItems)
      }
      val computedCount = convertPartialPlays(tracks.map { it.trackId }.toSet())
      if (newItems.isNotEmpty() || computedCount > 0) {
        dashboardRefresh.notifyUserPlaybackData()
        outboxPort.enqueue(DomainOutboxEvent.AppendPlaybackData())
      }
      recordFetchSuccess("recently_played")
      Unit.right()
    }
  }

  private fun recordFetchSuccess(operation: String) {
    val timestamp = lastFetchSuccessTimestamps.getOrPut(operation) {
      AtomicLong().also { atomic ->
        Gauge.builder("app.playback.last_success_timestamp", atomic) { it.get().toDouble() }
          .description("Epoch second timestamp of the last successful playback fetch")
          .tag("operation", operation)
          .register(meterRegistry)
      }
    }
    timestamp.set(Clock.System.now().toEpochMilliseconds() / MS_PER_SECOND)
  }

  private fun recordEventsIngested(source: String, count: Int) {
    meterRegistry.counter("app.playback.events_ingested", "source", source).increment(count.toDouble())
  }

  private fun deduplicateWithPartialPlays(newRecentlyPlayedItems: List<RecentlyPlayedItem>) {
    val recentlyPlayedWithStartTime = newRecentlyPlayedItems.filter { it.startTime != null }
    if (recentlyPlayedWithStartTime.isEmpty()) return

    val trackIds = recentlyPlayedWithStartTime.map { it.trackId }.toSet()
    val partialPlays = recentlyPartialPlayedRepository.findByTrackIds(trackIds)
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
      recentlyPartialPlayedRepository.deleteByPlayedAts(duplicatePlayedAts)
      appPlaybackRepository.deleteByPlayedAts(duplicatePlayedAts)
      duplicatePlayedAts
        .map { instant -> JLocalDate.ofInstant(instant.toJavaInstant(), ZoneOffset.UTC).toKotlinLocalDate() }
        .toSet()
        .forEach { day ->
          outboxPort.enqueue(DomainOutboxEvent.AggregatePlaybackData(AggregationPeriodType.DAY, day))
        }
    }
  }

  private fun convertPartialPlays(completedTrackIds: Set<TrackId>): Int {
    val sortedItems = currentlyPlayingRepository.findAll().sortedBy { it.observedAt }

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
      val existingPlayedAts = recentlyPartialPlayedRepository.findExistingPlayedAts(partialItems.map { it.playedAt }.toSet())
      val newPartial = partialItems.filter { it.playedAt !in existingPlayedAts }
      if (newPartial.isNotEmpty()) {
        recentlyPartialPlayedRepository.saveAll(newPartial)
        recordEventsIngested("partial_played", newPartial.size)
      }
      newPartial.size
    } else {
      0
    }

    // Delete completed tracks and all processed items (converted or skipped below threshold),
    // but don't delete the latest item's trackId as it may still be active
    val allProcessedTrackIds = itemsToProcess.map { it.trackId }.filter { it != latestItem?.trackId }.toSet()
    currentlyPlayingRepository.deleteByTrackIds((completedTrackIds + allProcessedTrackIds).map { it.value }.toSet())
    return newComputedCount
  }

  // --- Playback Data ---

  override fun enqueueRebuildPlaybackData() {
    currentUserResolver.userId() ?: return
    logger.info { "Enqueuing playback data rebuild" }
    outboxPort.enqueue(DomainOutboxEvent.RebuildPlaybackData())
  }

  override fun rebuildPlaybackData() {
    currentUserResolver.userId() ?: return
    logger.info { "Rebuilding playback data" }
    appPlaybackRepository.deleteAll()
    appendNewPlaybackData()
  }

  override fun appendPlaybackData() {
    currentUserResolver.userId() ?: return
    appendNewPlaybackData()
  }

  private fun appendNewPlaybackData() {
    val since = appPlaybackRepository.findMostRecentPlayedAt()
    val recentlyPlayed = recentlyPlayedRepository.findSince(since)
    val partialPlayed = recentlyPartialPlayedRepository.findSince(since)

    val allPlaybackItems = buildPlaybackItems(recentlyPlayed, partialPlayed)
    if (allPlaybackItems.isEmpty()) return

    val existingPlayedAts = appPlaybackRepository.findExistingPlayedAts(allPlaybackItems.map { it.playedAt }.toSet())
    val newPlaybackItems = allPlaybackItems.filter { it.playedAt !in existingPlayedAts }
    if (newPlaybackItems.isEmpty()) return

    appPlaybackRepository.saveAll(newPlaybackItems)

    newPlaybackItems
      .map { item -> JLocalDate.ofInstant(item.playedAt.toJavaInstant(), ZoneOffset.UTC).toKotlinLocalDate() }
      .toSet()
      .forEach { day ->
        outboxPort.enqueue(DomainOutboxEvent.AggregatePlaybackData(AggregationPeriodType.DAY, day))
      }
    outboxPort.enqueue(DomainOutboxEvent.RebuildDashboardReadModel())

    val catalogRequests = (
      recentlyPlayed.map { CatalogSyncRequest(it.trackId.value, listOfNotNull(it.artistIds.firstOrNull()?.value), SyncCause.Playback(it.trackId.value)) } +
        partialPlayed.map { CatalogSyncRequest(it.trackId.value, listOfNotNull(it.artistIds.firstOrNull()?.value), SyncCause.Playback(it.trackId.value)) }
    ).distinctBy { it.trackId }
    syncController.syncForTracks(catalogRequests)
  }

  private fun buildPlaybackItems(
    recentlyPlayed: List<RecentlyPlayedItem>,
    partialPlayed: List<RecentlyPartialPlayedItem>,
  ) = recentlyPlayed.map { item ->
    AppPlaybackItem(
      playedAt = item.playedAt,
      trackId = item.trackId.value,
      secondsPlayed = item.durationSeconds ?: 0L,
    )
  } + partialPlayed.map { item ->
    AppPlaybackItem(
      playedAt = item.playedAt,
      trackId = item.trackId.value,
      secondsPlayed = item.playedSeconds,
    )
  }

  // --- Outbox Handlers ---

  override fun handle(event: DomainOutboxEvent.FetchPlaybackData): Either<DomainError, Unit> =
    fetchPlaybackData()

  override fun handle(event: DomainOutboxEvent.RebuildPlaybackData): Either<DomainError, Unit> {
    rebuildPlaybackData()
    return Unit.right()
  }

  override fun handle(event: DomainOutboxEvent.AppendPlaybackData): Either<DomainError, Unit> {
    appendPlaybackData()
    return Unit.right()
  }

  companion object : KLogging() {
    private const val MS_PER_SECOND = 1_000L
    private const val PARTIAL_DUPLICATE_TOLERANCE_SECONDS = 8L
    private const val RESTART_THRESHOLD_MS = 10_000L
  }
}
