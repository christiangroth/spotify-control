package de.chrgroth.spotify.control.domain

import de.chrgroth.outbox.OutboxTaskResult
import de.chrgroth.spotify.control.domain.model.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.PlaylistType
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.PlaylistCheckPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaylistCheckRepositoryPort
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
@Suppress("Unused", "TooGenericExceptionCaught")
class PlaylistCheckAdapter(
    private val playlistRepository: PlaylistRepositoryPort,
    private val playlistCheckRepository: AppPlaylistCheckRepositoryPort,
    private val dashboardRefresh: DashboardRefreshPort,
    private val notification: PlaylistCheckNotificationPort,
    private val meterRegistry: MeterRegistry,
) : PlaylistCheckPort {

    override fun handle(event: DomainOutboxEvent.RunPlaylistChecks): OutboxTaskResult = try {
        val playlist = playlistRepository.findByUserIdAndPlaylistId(event.userId, event.playlistId)
        if (playlist == null) {
            logger.warn { "Playlist ${event.playlistId} not found for user ${event.userId.value}, skipping checks" }
            return OutboxTaskResult.Success
        }

        val playlistInfos = playlistRepository.findByUserId(event.userId)
        val currentPlaylistInfo = playlistInfos.find { it.spotifyPlaylistId == event.playlistId }

        val duplicateTrackIdCheck = timedCheck(CHECK_DUPLICATE_TRACK_IDS, event.playlistId) {
            runDuplicateTrackIdCheck(event.playlistId, playlist.tracks)
        }
        val previousDuplicateTrackIdCheck = playlistCheckRepository.findByCheckId(duplicateTrackIdCheck.checkId)
        playlistCheckRepository.save(duplicateTrackIdCheck)
        notifyIfChanged(previousDuplicateTrackIdCheck, duplicateTrackIdCheck)

        val allChecks = mutableListOf(duplicateTrackIdCheck)

        if (currentPlaylistInfo?.type == PlaylistType.YEAR) {
            val yearSongsInAllCheck = timedCheck(CHECK_YEAR_SONGS_IN_ALL, event.playlistId) {
                runYearSongsInAllCheck(event.userId, event.playlistId, playlistInfos, playlist.tracks)
            }
            val previousYearSongsInAllCheck = playlistCheckRepository.findByCheckId(yearSongsInAllCheck.checkId)
            playlistCheckRepository.save(yearSongsInAllCheck)
            notifyIfChanged(previousYearSongsInAllCheck, yearSongsInAllCheck)
            allChecks.add(yearSongsInAllCheck)
        }

        val totalViolations = allChecks.sumOf { it.violations.size }
        val status = if (totalViolations == 0) "all passed" else "$totalViolations violation(s)"
        logger.info { "Ran playlist checks for playlist ${event.playlistId} (user ${event.userId.value}): $status" }
        dashboardRefresh.notifyUserPlaylistChecks(event.userId)
        OutboxTaskResult.Success
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(RunPlaylistChecks) for playlist ${event.playlistId} (user ${event.userId.value})" }
        OutboxTaskResult.Failed("Unexpected error in playlist checks: ${e.message}", e)
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

    private fun runDuplicateTrackIdCheck(
        playlistId: String,
        tracks: List<PlaylistTrack>,
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
            checkId = "$playlistId:$CHECK_DUPLICATE_TRACK_IDS",
            playlistId = playlistId,
            lastCheck = Clock.System.now(),
            succeeded = violations.isEmpty(),
            violations = violations,
        )
    }

    private fun runYearSongsInAllCheck(
        userId: UserId,
        playlistId: String,
        playlistInfos: List<PlaylistInfo>,
        tracks: List<PlaylistTrack>,
    ): AppPlaylistCheck {
        val allPlaylistInfo = playlistInfos.find { it.type == PlaylistType.ALL }
        val allPlaylist = allPlaylistInfo?.let { playlistRepository.findByUserIdAndPlaylistId(userId, it.spotifyPlaylistId) }
        val violations = if (allPlaylist == null) {
            emptyList()
        } else {
            val allTrackIds = allPlaylist.tracks.map { it.trackId }.toSet()
            tracks
                .filter { it.trackId !in allTrackIds }
                .distinctBy { it.trackId }
                .map { track ->
                    val artistName = track.artistNames.firstOrNull() ?: track.artistIds.firstOrNull() ?: "Unknown Artist"
                    "$artistName – ${track.trackName}"
                }
        }
        return AppPlaylistCheck(
            checkId = "$playlistId:$CHECK_YEAR_SONGS_IN_ALL",
            playlistId = playlistId,
            lastCheck = Clock.System.now(),
            succeeded = violations.isEmpty(),
            violations = violations,
        )
    }

    companion object : KLogging() {
        private const val CHECK_DUPLICATE_TRACK_IDS = "duplicate-track-ids"
        private const val CHECK_YEAR_SONGS_IN_ALL = "year-songs-in-all"
    }
}
