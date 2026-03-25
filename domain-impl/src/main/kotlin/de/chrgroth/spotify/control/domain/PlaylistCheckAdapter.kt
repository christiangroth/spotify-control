package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.PlaylistCheckPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistCheckNotificationPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

@ApplicationScoped
@Suppress("Unused")
class PlaylistCheckAdapter(
    private val playlistRepository: PlaylistRepositoryPort,
    private val playlistCheckRepository: AppPlaylistCheckRepositoryPort,
    private val dashboardRefresh: DashboardRefreshPort,
    private val notification: PlaylistCheckNotificationPort,
    private val appTrackRepository: AppTrackRepositoryPort,
    private val meterRegistry: MeterRegistry,
) : PlaylistCheckPort {

    override fun handle(event: DomainOutboxEvent.RunPlaylistChecks): Either<DomainError, Unit> {
        val playlist = playlistRepository.findByUserIdAndPlaylistId(event.userId, event.playlistId)
        if (playlist == null) {
            logger.warn { "Playlist ${event.playlistId} not found for user ${event.userId.value}, skipping checks" }
            return Unit.right()
        }

        val duplicateTrackCheck = timedCheck(CHECK_DUPLICATE_TRACKS, event.playlistId) {
            runDuplicateTrackCheck(event.playlistId, playlist.tracks)
        }
        val previousDuplicateTrackCheck = playlistCheckRepository.findByCheckId(duplicateTrackCheck.checkId)
        playlistCheckRepository.save(duplicateTrackCheck)
        notifyIfChanged(previousDuplicateTrackCheck, duplicateTrackCheck)

        val status = if (duplicateTrackCheck.succeeded) "all passed" else "${duplicateTrackCheck.violations.size} violation(s)"
        logger.info { "Ran playlist checks for playlist ${event.playlistId} (user ${event.userId.value}): $status" }
        dashboardRefresh.notifyUserPlaylistChecks(event.userId)
        return Unit.right()
    }

    private fun notifyIfChanged(previous: AppPlaylistCheck?, current: AppPlaylistCheck) {
        if (previous == null) return
        if (!previous.succeeded && current.succeeded) {
            notification.notifyCheckPassed(current)
        } else if (!previous.succeeded && !current.succeeded && previous.violations != current.violations) {
            notification.notifyViolationsChanged(current)
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

    private fun runDuplicateTrackCheck(
        playlistId: String,
        tracks: List<de.chrgroth.spotify.control.domain.model.PlaylistTrack>,
    ): AppPlaylistCheck {
        val countByTrackId = tracks.groupingBy { it.trackId }.eachCount()
        val duplicateTrackIds = tracks
            .distinctBy { it.trackId }
            .filter { (countByTrackId[it.trackId] ?: 0) > 1 }
            .map { it.trackId }
        val appTrackById = if (duplicateTrackIds.isNotEmpty()) {
            appTrackRepository.findByTrackIds(duplicateTrackIds.map { TrackId(it) }.toSet()).associateBy { it.id.value }
        } else {
            emptyMap()
        }
        val violations = duplicateTrackIds.map { trackId ->
            val appTrack = requireNotNull(appTrackById[trackId]) { "Track $trackId not found in catalog" }
            val artistName = appTrack.artistName ?: "Unknown Artist"
            "$artistName – ${appTrack.title}"
        }
        return AppPlaylistCheck(
            checkId = "$playlistId:$CHECK_DUPLICATE_TRACKS",
            playlistId = playlistId,
            lastCheck = Clock.System.now(),
            succeeded = violations.isEmpty(),
            violations = violations,
        )
    }

    companion object : KLogging() {
        private const val CHECK_DUPLICATE_TRACKS = "duplicate-tracks"
    }
}
