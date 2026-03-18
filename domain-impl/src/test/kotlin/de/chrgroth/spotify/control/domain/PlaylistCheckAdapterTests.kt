package de.chrgroth.spotify.control.domain

import de.chrgroth.outbox.OutboxTaskResult
import de.chrgroth.spotify.control.domain.model.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.Playlist
import de.chrgroth.spotify.control.domain.model.PlaylistTrack
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
    private val event = DomainOutboxEvent.RunPlaylistChecks(userId, playlistId)
    private val checkId = "$playlistId:duplicate-tracks"

    private fun buildTrack(trackId: String, artistName: String = "Artist", trackName: String = "Track $trackId") = PlaylistTrack(
        trackId = trackId,
        trackName = trackName,
        artistIds = listOf("artist-1"),
        artistNames = listOf(artistName),
        albumId = "album-1",
    )

    private fun buildPlaylist(tracks: List<PlaylistTrack>) = Playlist(
        spotifyPlaylistId = playlistId,
        snapshotId = "snap-1",
        tracks = tracks,
    )

    private fun buildCheck(succeeded: Boolean, violations: List<String> = emptyList()) = AppPlaylistCheck(
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
    fun `handle runs check and saves result with no previous check - no notification`() {
        val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t2")))
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { playlistCheckRepository.findByCheckId(checkId) } returns null
        every { playlistCheckRepository.save(any()) } just runs
        every { dashboardRefresh.notifyUserPlaylistChecks(userId) } just runs

        val result = adapter.handle(event)

        assertThat(result).isEqualTo(OutboxTaskResult.Success)
        verify(exactly = 1) { playlistCheckRepository.save(any()) }
        verify(exactly = 0) { notification.notifyCheckPassed(any()) }
        verify(exactly = 0) { notification.notifyViolationsChanged(any()) }
    }

    @Test
    fun `handle sends notifyCheckPassed when check changes from failed to passed`() {
        val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t2")))
        val previousCheck = buildCheck(succeeded = false, violations = listOf("Artist – Track t1"))
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { playlistCheckRepository.findByCheckId(checkId) } returns previousCheck
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
            listOf(
                buildTrack("t1", artistName = "Artist A", trackName = "Track A"),
                buildTrack("t1", artistName = "Artist A", trackName = "Track A"),
                buildTrack("t2", artistName = "Artist B", trackName = "Track B"),
                buildTrack("t2", artistName = "Artist B", trackName = "Track B"),
            ),
        )
        val previousCheck = buildCheck(succeeded = false, violations = listOf("Artist A – Track A"))
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { playlistCheckRepository.findByCheckId(checkId) } returns previousCheck
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
            listOf(
                buildTrack("t1", artistName = "Artist A", trackName = "Track A"),
                buildTrack("t1", artistName = "Artist A", trackName = "Track A"),
            ),
        )
        val previousCheck = buildCheck(succeeded = false, violations = listOf("Artist A – Track A"))
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { playlistCheckRepository.findByCheckId(checkId) } returns previousCheck
        every { playlistCheckRepository.save(any()) } just runs
        every { dashboardRefresh.notifyUserPlaylistChecks(userId) } just runs

        val result = adapter.handle(event)

        assertThat(result).isEqualTo(OutboxTaskResult.Success)
        verify(exactly = 0) { notification.notifyCheckPassed(any()) }
        verify(exactly = 0) { notification.notifyViolationsChanged(any()) }
    }

    @Test
    fun `handle does not send notification when check stays passed`() {
        val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t2")))
        val previousCheck = buildCheck(succeeded = true)
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { playlistCheckRepository.findByCheckId(checkId) } returns previousCheck
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
}
