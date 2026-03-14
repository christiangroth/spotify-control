package de.chrgroth.spotify.control.domain

import de.chrgroth.outbox.OutboxTaskResult
import de.chrgroth.spotify.control.domain.model.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.PlaylistCheckPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class PlaylistCheckAdapter(
    private val playlistRepository: PlaylistRepositoryPort,
    private val playlistCheckRepository: AppPlaylistCheckRepositoryPort,
    private val dashboardRefresh: DashboardRefreshPort,
    private val meterRegistry: MeterRegistry,
) : PlaylistCheckPort {

    override fun handle(event: DomainOutboxEvent.RunPlaylistChecks): OutboxTaskResult = try {
        val playlist = playlistRepository.findByUserIdAndPlaylistId(event.userId, event.playlistId)
        if (playlist == null) {
            logger.warn { "Playlist ${event.playlistId} not found for user ${event.userId.value}, skipping checks" }
            return OutboxTaskResult.Success
        }

        val duplicateTrackCheck = timedCheck(CHECK_DUPLICATE_TRACKS, event.playlistId) {
            runDuplicateTrackCheck(event.playlistId, playlist.tracks)
        }
        playlistCheckRepository.save(duplicateTrackCheck)

        val status = if (duplicateTrackCheck.succeeded) "all passed" else "${duplicateTrackCheck.violations.size} violation(s)"
        logger.info { "Ran playlist checks for playlist ${event.playlistId} (user ${event.userId.value}): $status" }
        dashboardRefresh.notifyUserPlaylistChecks(event.userId)
        OutboxTaskResult.Success
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(RunPlaylistChecks) for playlist ${event.playlistId} (user ${event.userId.value})" }
        OutboxTaskResult.Failed("Unexpected error in playlist checks: ${e.message}", e)
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
        val violations = tracks
            .distinctBy { it.trackId }
            .filter { (countByTrackId[it.trackId] ?: 0) > 1 }
            .map { track ->
                val artistName = track.artistNames.firstOrNull() ?: track.artistIds.firstOrNull() ?: "Unknown Artist"
                "$artistName – ${track.trackName}"
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
