package de.chrgroth.spotify.control.domain.playlist

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.PlaylistFixError
import de.chrgroth.spotify.control.domain.playlist.check.PlaylistCheckRunner
import de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckDashboard
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckDashboardSummary
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistCheckPort
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistCheckNotificationPort
import de.chrgroth.spotify.control.domain.port.out.readmodel.PlaylistCheckDashboardRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import de.chrgroth.spotify.control.domain.user.CurrentUserResolver
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import mu.KLogging
import org.eclipse.microprofile.context.ManagedExecutor
import java.util.concurrent.TimeUnit

@ApplicationScoped
@Suppress("Unused")
class PlaylistCheckService(
  private val checkRunners: Instance<PlaylistCheckRunner>,
  private val playlistRepository: PlaylistRepositoryPort,
  private val playlistCheckRepository: AppPlaylistCheckRepositoryPort,
  private val userRepository: UserRepositoryPort,
  private val currentUserResolver: CurrentUserResolver,
  private val dashboardRefresh: DashboardRefreshPort,
  private val notification: PlaylistCheckNotificationPort,
  private val spotifyAccessToken: SpotifyAccessTokenPort,
  private val outboxPort: OutboxPort,
  private val playlistCheckDashboardRepository: PlaylistCheckDashboardRepositoryPort,
  private val meterRegistry: MeterRegistry,
  private val managedExecutor: ManagedExecutor,
) : PlaylistCheckPort {

  override fun handle(event: DomainOutboxEvent.RunPlaylistChecks): Either<DomainError, Unit> {
    currentUserResolver.userId() ?: return Unit.right()
    val playlist = playlistRepository.findByPlaylistId(event.playlistId)
    if (playlist == null) {
      logger.warn { "Playlist ${event.playlistId} not found, skipping checks" }
      return Unit.right()
    }

    val allPlaylistInfos = playlistRepository.findAll()
    val currentPlaylistInfo = allPlaylistInfos.find { it.spotifyPlaylistId == event.playlistId }

    val applicableRunners = checkRunners
      .filter { it.isApplicable(currentPlaylistInfo) }
      .filter { event.checkType == null || it.checkId == event.checkType }
    val results = applicableRunners
      .map { runner ->
        managedExecutor.supplyAsync {
          timedCheck(runner.checkId, event.playlistId) {
            runner.run(event.playlistId, playlist, currentPlaylistInfo, allPlaylistInfos)
          }
        }
      }
      .map { it.join() }

    results.forEach { check ->
      val previous = playlistCheckRepository.findByCheckId(check.checkId)
      playlistCheckRepository.save(check)
      notifyIfChanged(previous, check, currentPlaylistInfo?.name)
    }

    val totalViolations = results.sumOf { it.violations.size }
    val status = if (totalViolations == 0) "all passed" else "$totalViolations violation(s)"
    logger.info { "Ran playlist checks for playlist ${event.playlistId}: $status" }
    dashboardRefresh.notifyUserPlaylistChecks()
    rebuildCheckDashboard()
    outboxPort.enqueue(DomainOutboxEvent.RebuildDashboardReadModel())
    return Unit.right()
  }

  override fun getCheckDashboard(): PlaylistCheckDashboard {
    val summary = playlistCheckDashboardRepository.find() ?: EMPTY_SUMMARY
    return PlaylistCheckDashboard(
      displayName = summary.displayName,
      checks = summary.checks,
      playlistNameById = summary.playlistNameById,
      displayNames = getDisplayNames(),
      fixableCheckIds = getFixableCheckIds(),
    )
  }

  override fun rebuildCheckDashboard() {
    playlistCheckDashboardRepository.save(buildCheckDashboardSummary())
  }

  private fun buildCheckDashboardSummary(): PlaylistCheckDashboardSummary {
    val displayNameFuture = managedExecutor.supplyAsync { userRepository.get()?.displayName ?: "" }
    val playlistNamesFuture = managedExecutor.supplyAsync {
      playlistRepository.findAll().associateBy({ it.spotifyPlaylistId }, { it.name })
    }
    val checksFuture = managedExecutor.supplyAsync { playlistCheckRepository.findAll() }
    return PlaylistCheckDashboardSummary(
      displayName = displayNameFuture.join(),
      playlistNameById = playlistNamesFuture.join(),
      checks = checksFuture.join(),
    )
  }

  override fun getDisplayNames(): Map<String, String> =
    checkRunners.associate { it.checkId to it.displayName }

  override fun getFixableCheckIds(): Set<String> =
    checkRunners.filter { it.canFix() }.map { it.checkId }.toSet()

  override fun enqueueFix(playlistId: String, checkType: String, violationIds: Set<String>): Either<DomainError, Unit> {
    currentUserResolver.userId() ?: return PlaylistFixError.PLAYLIST_NOT_FOUND.left()
    if (violationIds.isEmpty()) {
      return PlaylistFixError.NO_VIOLATIONS_SELECTED.left()
    }
    checkRunners.find { it.checkId == checkType && it.canFix() } ?: run {
      logger.warn { "No fix runner found for checkType $checkType" }
      return PlaylistFixError.FIX_NOT_FOUND.left()
    }
    playlistRepository.findByPlaylistId(playlistId) ?: run {
      logger.warn { "Playlist $playlistId not found" }
      return PlaylistFixError.PLAYLIST_NOT_FOUND.left()
    }
    logger.info { "Enqueueing fix '$checkType' for playlist $playlistId (${violationIds.size} violation(s) selected)" }
    outboxPort.enqueue(DomainOutboxEvent.FixPlaylistCheck(playlistId, checkType, violationIds))
    return Unit.right()
  }

  override fun enqueueRunAllChecks() {
    currentUserResolver.userId() ?: return
    val activePlaylistIds = playlistRepository.findAll()
      .filter { it.syncStatus == PlaylistSyncStatus.ACTIVE }
      .map { it.spotifyPlaylistId }
    logger.info { "Enqueueing playlist checks for ${activePlaylistIds.size} active playlist(s)" }
    activePlaylistIds.forEach { playlistId -> outboxPort.enqueue(DomainOutboxEvent.RunPlaylistChecks(playlistId)) }
  }

  override fun enqueueRunCheck(checkType: String) {
    currentUserResolver.userId() ?: return
    val activePlaylistIds = playlistRepository.findAll()
      .filter { it.syncStatus == PlaylistSyncStatus.ACTIVE }
      .map { it.spotifyPlaylistId }
    logger.info { "Enqueueing check '$checkType' for ${activePlaylistIds.size} active playlist(s)" }
    activePlaylistIds.forEach { playlistId -> outboxPort.enqueue(DomainOutboxEvent.RunPlaylistChecks(playlistId, checkType)) }
  }

  override fun handle(event: DomainOutboxEvent.FixPlaylistCheck): Either<DomainError, Unit> {
    currentUserResolver.userId() ?: return Unit.right()
    val runner = checkRunners.find { it.checkId == event.checkType && it.canFix() } ?: run {
      logger.warn { "No fix runner found for checkType ${event.checkType}" }
      return PlaylistFixError.FIX_NOT_FOUND.left()
    }
    val playlist = playlistRepository.findByPlaylistId(event.playlistId) ?: run {
      logger.warn { "Playlist ${event.playlistId} not found" }
      return PlaylistFixError.PLAYLIST_NOT_FOUND.left()
    }
    val allPlaylistInfos = playlistRepository.findAll()
    val currentPlaylistInfo = allPlaylistInfos.find { it.spotifyPlaylistId == event.playlistId }
    val accessToken = spotifyAccessToken.getValidAccessToken()
    logger.info { "Running fix '${event.checkType}' for playlist ${event.playlistId}" }
    return runner.fix(accessToken, event.playlistId, playlist, currentPlaylistInfo, allPlaylistInfos, event.violationIds).also { result ->
      if (result.isRight()) {
        logger.info { "Fix '${event.checkType}' for playlist ${event.playlistId} completed, enqueueing re-check" }
        outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(event.playlistId))
      }
    }
  }

  private fun notifyIfChanged(previous: AppPlaylistCheck?, current: AppPlaylistCheck, playlistName: String?) {
    if (previous == null) {
      if (!current.succeeded) {
        notification.notifyViolationsChanged(current, playlistName, previousViolationCount = null)
      }
      return
    }
    if (!previous.succeeded && current.succeeded) {
      notification.notifyCheckPassed(current, playlistName)
    } else if (!previous.succeeded && !current.succeeded && previous.violations != current.violations) {
      notification.notifyViolationsChanged(current, playlistName, previousViolationCount = previous.violations.size)
    }
  }

  private fun <T> timedCheck(checkId: String, playlistId: String, block: () -> T): T {
    val startNs = System.nanoTime()
    val result = block()
    val durationNs = System.nanoTime() - startNs
    Timer.builder("playlist.check")
      .tag("checkId", checkId)
      .tag("playlistId", playlistId)
      .register(meterRegistry)
      .record(durationNs, TimeUnit.NANOSECONDS)
    return result
  }

  companion object : KLogging() {
    private val EMPTY_SUMMARY = PlaylistCheckDashboardSummary(displayName = "", checks = emptyList(), playlistNameById = emptyMap())
  }
}
