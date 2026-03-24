package de.chrgroth.spotify.control.domain

import de.chrgroth.outbox.OutboxTaskResult
import de.chrgroth.spotify.control.domain.model.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.Playlist
import de.chrgroth.spotify.control.domain.model.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.PlaylistType
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistCheckNotificationPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Clock

class PlaylistCheckAdapterTests {

    private val playlistRepository: PlaylistRepositoryPort = mockk()
    private val playlistCheckRepository: AppPlaylistCheckRepositoryPort = mockk()
    private val dashboardRefresh: DashboardRefreshPort = mockk()
    private val notification: PlaylistCheckNotificationPort = mockk()
    private val meterRegistry = SimpleMeterRegistry()

    private val adapter = PlaylistCheckAdapter(
        playlistRepository,
        playlistCheckRepository,
        dashboardRefresh,
        notification,
        meterRegistry,
    )

    private val userId = UserId("user-1")
    private val playlistId = "playlist-1"
    private val allPlaylistId = "playlist-all"
    private val event = DomainOutboxEvent.RunPlaylistChecks(userId, playlistId)
    private val duplicateCheckId = "$playlistId:duplicate-track-ids"
    private val yearCheckId = "$playlistId:year-songs-in-all"

    private fun buildTrack(trackId: String, artistName: String = "Artist", trackName: String = "Track $trackId") = PlaylistTrack(
        trackId = trackId,
        trackName = trackName,
        artistIds = listOf("artist-1"),
        artistNames = listOf(artistName),
        albumId = "album-1",
    )

    private fun buildPlaylist(spotifyPlaylistId: String = playlistId, tracks: List<PlaylistTrack>) = Playlist(
        spotifyPlaylistId = spotifyPlaylistId,
        snapshotId = "snap-1",
        tracks = tracks,
    )

    private fun buildPlaylistInfo(spotifyPlaylistId: String = playlistId, type: PlaylistType? = null) = PlaylistInfo(
        spotifyPlaylistId = spotifyPlaylistId,
        snapshotId = "snap-1",
        lastSnapshotIdSyncTime = Clock.System.now(),
        name = "Playlist $spotifyPlaylistId",
        syncStatus = PlaylistSyncStatus.ACTIVE,
        type = type,
    )

    private fun buildCheck(checkId: String, succeeded: Boolean, violations: List<String> = emptyList()) = AppPlaylistCheck(
        checkId = checkId,
        playlistId = playlistId,
        lastCheck = Clock.System.now(),
        succeeded = succeeded,
        violations = violations,
    )

    @Test
    fun `handle returns success and skips notifications when playlist not found`() {
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns null

        val result = adapter.handle(event)

        assertThat(result).isEqualTo(OutboxTaskResult.Success)
        verify(exactly = 0) { playlistCheckRepository.findByCheckId(any()) }
        verify(exactly = 0) { notification.notifyCheckPassed(any()) }
        verify(exactly = 0) { notification.notifyViolationsChanged(any()) }
    }

    @Test
    fun `handle runs duplicate-track-ids check and saves result with no previous check - no notification`() {
        val playlist = buildPlaylist(tracks = listOf(buildTrack("t1"), buildTrack("t2")))
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo())
        every { playlistCheckRepository.findByCheckId(duplicateCheckId) } returns null
        every { playlistCheckRepository.save(any()) } just runs
        every { dashboardRefresh.notifyUserPlaylistChecks(userId) } just runs

        val result = adapter.handle(event)

        assertThat(result).isEqualTo(OutboxTaskResult.Success)
        verify(exactly = 1) { playlistCheckRepository.save(any()) }
        verify(exactly = 0) { notification.notifyCheckPassed(any()) }
        verify(exactly = 0) { notification.notifyViolationsChanged(any()) }
    }

    @Test
    fun `handle sends notifyCheckPassed when duplicate-track-ids check changes from failed to passed`() {
        val playlist = buildPlaylist(tracks = listOf(buildTrack("t1"), buildTrack("t2")))
        val previousCheck = buildCheck(duplicateCheckId, succeeded = false, violations = listOf("Artist – Track t1"))
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo())
        every { playlistCheckRepository.findByCheckId(duplicateCheckId) } returns previousCheck
        every { playlistCheckRepository.save(any()) } just runs
        every { dashboardRefresh.notifyUserPlaylistChecks(userId) } just runs
        every { notification.notifyCheckPassed(any()) } just runs

        val result = adapter.handle(event)

        assertThat(result).isEqualTo(OutboxTaskResult.Success)
        verify(exactly = 1) { notification.notifyCheckPassed(any()) }
        verify(exactly = 0) { notification.notifyViolationsChanged(any()) }
    }

    @Test
    fun `handle sends notifyViolationsChanged when check stays failed with different violations`() {
        val playlist = buildPlaylist(
            tracks = listOf(
                buildTrack("t1", artistName = "Artist A", trackName = "Track A"),
                buildTrack("t1", artistName = "Artist A", trackName = "Track A"),
                buildTrack("t2", artistName = "Artist B", trackName = "Track B"),
                buildTrack("t2", artistName = "Artist B", trackName = "Track B"),
            ),
        )
        val previousCheck = buildCheck(duplicateCheckId, succeeded = false, violations = listOf("Artist A – Track A"))
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo())
        every { playlistCheckRepository.findByCheckId(duplicateCheckId) } returns previousCheck
        every { playlistCheckRepository.save(any()) } just runs
        every { dashboardRefresh.notifyUserPlaylistChecks(userId) } just runs
        every { notification.notifyViolationsChanged(any()) } just runs

        val result = adapter.handle(event)

        assertThat(result).isEqualTo(OutboxTaskResult.Success)
        verify(exactly = 0) { notification.notifyCheckPassed(any()) }
        verify(exactly = 1) { notification.notifyViolationsChanged(any()) }
    }

    @Test
    fun `handle does not send notification when check stays failed with same violations`() {
        val playlist = buildPlaylist(
            tracks = listOf(
                buildTrack("t1", artistName = "Artist A", trackName = "Track A"),
                buildTrack("t1", artistName = "Artist A", trackName = "Track A"),
            ),
        )
        val previousCheck = buildCheck(duplicateCheckId, succeeded = false, violations = listOf("Artist A – Track A"))
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo())
        every { playlistCheckRepository.findByCheckId(duplicateCheckId) } returns previousCheck
        every { playlistCheckRepository.save(any()) } just runs
        every { dashboardRefresh.notifyUserPlaylistChecks(userId) } just runs

        val result = adapter.handle(event)

        assertThat(result).isEqualTo(OutboxTaskResult.Success)
        verify(exactly = 0) { notification.notifyCheckPassed(any()) }
        verify(exactly = 0) { notification.notifyViolationsChanged(any()) }
    }

    @Test
    fun `handle does not send notification when check stays passed`() {
        val playlist = buildPlaylist(tracks = listOf(buildTrack("t1"), buildTrack("t2")))
        val previousCheck = buildCheck(duplicateCheckId, succeeded = true)
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo())
        every { playlistCheckRepository.findByCheckId(duplicateCheckId) } returns previousCheck
        every { playlistCheckRepository.save(any()) } just runs
        every { dashboardRefresh.notifyUserPlaylistChecks(userId) } just runs

        val result = adapter.handle(event)

        assertThat(result).isEqualTo(OutboxTaskResult.Success)
        verify(exactly = 0) { notification.notifyCheckPassed(any()) }
        verify(exactly = 0) { notification.notifyViolationsChanged(any()) }
    }

    @Test
    fun `handle returns failed result on unexpected exception`() {
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } throws RuntimeException("DB error")

        val result = adapter.handle(event)

        assertThat(result).isInstanceOf(OutboxTaskResult.Failed::class.java)
    }

    @Test
    fun `year-songs-in-all check is skipped for non-year playlists`() {
        val playlist = buildPlaylist(tracks = listOf(buildTrack("t1")))
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo(type = PlaylistType.ALL))
        every { playlistCheckRepository.findByCheckId(duplicateCheckId) } returns null
        every { playlistCheckRepository.save(any()) } just runs
        every { dashboardRefresh.notifyUserPlaylistChecks(userId) } just runs

        adapter.handle(event)

        verify(exactly = 1) { playlistCheckRepository.save(any()) }
        verify(exactly = 0) { playlistCheckRepository.findByCheckId(yearCheckId) }
    }

    @Test
    fun `year-songs-in-all check passes when no all playlist exists`() {
        val playlist = buildPlaylist(tracks = listOf(buildTrack("t1")))
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo(type = PlaylistType.YEAR))
        every { playlistCheckRepository.findByCheckId(duplicateCheckId) } returns null
        every { playlistCheckRepository.findByCheckId(yearCheckId) } returns null
        every { playlistCheckRepository.save(any()) } just runs
        every { dashboardRefresh.notifyUserPlaylistChecks(userId) } just runs

        val result = adapter.handle(event)

        assertThat(result).isEqualTo(OutboxTaskResult.Success)
        verify(exactly = 2) { playlistCheckRepository.save(any()) }
        verify(exactly = 1) {
            playlistCheckRepository.save(
                match { it.checkId == yearCheckId && it.succeeded && it.violations.isEmpty() },
            )
        }
    }

    @Test
    fun `year-songs-in-all check passes when all playlist is not yet synced`() {
        val playlist = buildPlaylist(tracks = listOf(buildTrack("t1")))
        val allPlaylistInfo = buildPlaylistInfo(spotifyPlaylistId = allPlaylistId, type = PlaylistType.ALL)
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo(type = PlaylistType.YEAR), allPlaylistInfo)
        every { playlistRepository.findByUserIdAndPlaylistId(userId, allPlaylistId) } returns null
        every { playlistCheckRepository.findByCheckId(duplicateCheckId) } returns null
        every { playlistCheckRepository.findByCheckId(yearCheckId) } returns null
        every { playlistCheckRepository.save(any()) } just runs
        every { dashboardRefresh.notifyUserPlaylistChecks(userId) } just runs

        val result = adapter.handle(event)

        assertThat(result).isEqualTo(OutboxTaskResult.Success)
        verify(exactly = 1) {
            playlistCheckRepository.save(
                match { it.checkId == yearCheckId && it.succeeded && it.violations.isEmpty() },
            )
        }
    }

    @Test
    fun `year-songs-in-all check passes when all tracks are in all playlist`() {
        val playlist = buildPlaylist(tracks = listOf(buildTrack("t1"), buildTrack("t2")))
        val allPlaylist = buildPlaylist(spotifyPlaylistId = allPlaylistId, tracks = listOf(buildTrack("t1"), buildTrack("t2"), buildTrack("t3")))
        val allPlaylistInfo = buildPlaylistInfo(spotifyPlaylistId = allPlaylistId, type = PlaylistType.ALL)
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo(type = PlaylistType.YEAR), allPlaylistInfo)
        every { playlistRepository.findByUserIdAndPlaylistId(userId, allPlaylistId) } returns allPlaylist
        every { playlistCheckRepository.findByCheckId(duplicateCheckId) } returns null
        every { playlistCheckRepository.findByCheckId(yearCheckId) } returns null
        every { playlistCheckRepository.save(any()) } just runs
        every { dashboardRefresh.notifyUserPlaylistChecks(userId) } just runs

        val result = adapter.handle(event)

        assertThat(result).isEqualTo(OutboxTaskResult.Success)
        verify(exactly = 1) {
            playlistCheckRepository.save(
                match { it.checkId == yearCheckId && it.succeeded && it.violations.isEmpty() },
            )
        }
    }

    @Test
    fun `year-songs-in-all check reports violations for tracks missing from all playlist`() {
        val playlist = buildPlaylist(
            tracks = listOf(
                buildTrack("t1", artistName = "Artist A", trackName = "Song A"),
                buildTrack("t2", artistName = "Artist B", trackName = "Song B"),
                buildTrack("t3", artistName = "Artist C", trackName = "Song C"),
            ),
        )
        val allPlaylist = buildPlaylist(spotifyPlaylistId = allPlaylistId, tracks = listOf(buildTrack("t1")))
        val allPlaylistInfo = buildPlaylistInfo(spotifyPlaylistId = allPlaylistId, type = PlaylistType.ALL)
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo(type = PlaylistType.YEAR), allPlaylistInfo)
        every { playlistRepository.findByUserIdAndPlaylistId(userId, allPlaylistId) } returns allPlaylist
        every { playlistCheckRepository.findByCheckId(duplicateCheckId) } returns null
        every { playlistCheckRepository.findByCheckId(yearCheckId) } returns null
        every { playlistCheckRepository.save(any()) } just runs
        every { dashboardRefresh.notifyUserPlaylistChecks(userId) } just runs

        val result = adapter.handle(event)

        assertThat(result).isEqualTo(OutboxTaskResult.Success)
        verify(exactly = 1) {
            playlistCheckRepository.save(
                match { check ->
                    check.checkId == yearCheckId &&
                        !check.succeeded &&
                        check.violations.size == 2 &&
                        check.violations.contains("Artist B – Song B") &&
                        check.violations.contains("Artist C – Song C")
                },
            )
        }
    }

    @Test
    fun `year-songs-in-all check deduplicates violations for duplicate missing tracks`() {
        val playlist = buildPlaylist(
            tracks = listOf(
                buildTrack("t1", artistName = "Artist A", trackName = "Song A"),
                buildTrack("t2", artistName = "Artist B", trackName = "Song B"),
                buildTrack("t2", artistName = "Artist B", trackName = "Song B"),
            ),
        )
        val allPlaylist = buildPlaylist(spotifyPlaylistId = allPlaylistId, tracks = listOf(buildTrack("t1")))
        val allPlaylistInfo = buildPlaylistInfo(spotifyPlaylistId = allPlaylistId, type = PlaylistType.ALL)
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo(type = PlaylistType.YEAR), allPlaylistInfo)
        every { playlistRepository.findByUserIdAndPlaylistId(userId, allPlaylistId) } returns allPlaylist
        every { playlistCheckRepository.findByCheckId(duplicateCheckId) } returns null
        every { playlistCheckRepository.findByCheckId(yearCheckId) } returns null
        every { playlistCheckRepository.save(any()) } just runs
        every { dashboardRefresh.notifyUserPlaylistChecks(userId) } just runs

        adapter.handle(event)

        verify(exactly = 1) {
            playlistCheckRepository.save(
                match { check ->
                    check.checkId == yearCheckId &&
                        !check.succeeded &&
                        check.violations.size == 1 &&
                        check.violations.contains("Artist B – Song B")
                },
            )
        }
    }
}
