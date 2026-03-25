package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.check.PlaylistCheckRunner
import de.chrgroth.spotify.control.domain.model.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.ArtistId
import de.chrgroth.spotify.control.domain.model.Playlist
import de.chrgroth.spotify.control.domain.model.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistCheckNotificationPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import jakarta.enterprise.inject.Instance
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

class PlaylistCheckAdapterTests {

    private val checkRunner: PlaylistCheckRunner = mockk()
    private val checkRunners: Instance<PlaylistCheckRunner> = mockk()
    private val playlistRepository: PlaylistRepositoryPort = mockk()
    private val playlistCheckRepository: AppPlaylistCheckRepositoryPort = mockk()
    private val dashboardRefresh: DashboardRefreshPort = mockk()
    private val notification: PlaylistCheckNotificationPort = mockk()
    private val appTrackRepository: AppTrackRepositoryPort = mockk()
    private val meterRegistry = SimpleMeterRegistry()

    private val adapter = PlaylistCheckAdapter(
        checkRunners,
        playlistRepository,
        playlistCheckRepository,
        dashboardRefresh,
        notification,
        appTrackRepository,
        meterRegistry,
    )

    private val userId = UserId("user-1")
    private val playlistId = "playlist-1"
    private val event = DomainOutboxEvent.RunPlaylistChecks(userId, playlistId)
    private val checkId = "test-check"
    private val fullCheckId = "$playlistId:$checkId"

    private fun buildTrack(trackId: String, artistId: String = "artist-1") = PlaylistTrack(
        trackId = trackId,
        artistIds = listOf(artistId),
        albumId = "album-1",
    )

    private fun buildAppTrack(trackId: String, title: String, artistName: String) = AppTrack(
        id = TrackId(trackId),
        title = title,
        artistId = ArtistId("artist-1"),
        artistName = artistName,
        lastSync = Instant.fromEpochMilliseconds(0),
    )

    private fun buildPlaylist(tracks: List<PlaylistTrack>) = Playlist(
        spotifyPlaylistId = playlistId,
        tracks = tracks,
    )

    private fun buildPlaylistInfo() = PlaylistInfo(
        spotifyPlaylistId = playlistId,
        snapshotId = "snap-1",
        lastSnapshotIdSyncTime = Clock.System.now(),
        name = "Playlist $playlistId",
        syncStatus = PlaylistSyncStatus.ACTIVE,
        type = null,
    )

    private fun buildCheck(succeeded: Boolean, violations: List<String> = emptyList()) = AppPlaylistCheck(
        checkId = fullCheckId,
        playlistId = playlistId,
        lastCheck = Clock.System.now(),
        succeeded = succeeded,
        violations = violations,
    )

    private fun setupCheckRunner(check: AppPlaylistCheck) {
        every { checkRunner.checkId } returns checkId
        every { checkRunner.displayName } returns "Test Check"
        every { checkRunner.isApplicable(any()) } returns true
        every { checkRunner.run(any(), any(), any(), any(), any()) } returns check
        every { checkRunners.iterator() } answers { mutableListOf(checkRunner).iterator() }
    }

    @Test
    fun `handle returns success and skips notifications when playlist not found`() {
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns null

        val result = adapter.handle(event)

        assertThat(result.isRight()).isTrue()
        verify(exactly = 0) { playlistCheckRepository.findByCheckId(any()) }
        verify(exactly = 0) { notification.notifyCheckPassed(any()) }
        verify(exactly = 0) { notification.notifyViolationsChanged(any()) }
    }

    @Test
    fun `handle runs check and saves result with no previous check - no notification`() {
        val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t2")))
        val check = buildCheck(succeeded = true)
        setupCheckRunner(check)
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { appTrackRepository.findByTrackIds(any()) } returns emptyList()
        every { playlistCheckRepository.findByCheckId(checkId) } returns null
        every { playlistCheckRepository.save(any()) } just runs
        every { dashboardRefresh.notifyUserPlaylistChecks(userId) } just runs

        val result = adapter.handle(event)

        assertThat(result.isRight()).isTrue()
        verify(exactly = 1) { playlistCheckRepository.save(any()) }
        verify(exactly = 0) { notification.notifyCheckPassed(any()) }
        verify(exactly = 0) { notification.notifyViolationsChanged(any()) }
    }

    @Test
    fun `handle sends notifyCheckPassed when check changes from failed to passed`() {
        val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t2")))
        val check = buildCheck(succeeded = true)
        val previousCheck = buildCheck(succeeded = false, violations = listOf("Artist – Track t1"))
        setupCheckRunner(check)
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { appTrackRepository.findByTrackIds(any()) } returns emptyList()
        every { playlistCheckRepository.findByCheckId(checkId) } returns previousCheck
        every { playlistCheckRepository.save(any()) } just runs
        every { dashboardRefresh.notifyUserPlaylistChecks(userId) } just runs
        every { notification.notifyCheckPassed(any()) } just runs

        val result = adapter.handle(event)

        assertThat(result.isRight()).isTrue()
        verify(exactly = 1) { notification.notifyCheckPassed(any()) }
        verify(exactly = 0) { notification.notifyViolationsChanged(any()) }
    }

    @Test
    fun `handle sends notifyViolationsChanged when check stays failed with different violations`() {
        val playlist = buildPlaylist(
            listOf(
                buildTrack("t1"),
                buildTrack("t1"),
                buildTrack("t2"),
                buildTrack("t2"),
            ),
        )
        val appTrackT1 = buildAppTrack("t1", "Track A", "Artist A")
        val appTrackT2 = buildAppTrack("t2", "Track B", "Artist B")
        val previousCheck = buildCheck(succeeded = false, violations = listOf("Artist A – Track A"))
        setupCheckRunner(check)
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"), TrackId("t2"))) } returns listOf(appTrackT1, appTrackT2)
        every { playlistCheckRepository.findByCheckId(checkId) } returns previousCheck
        every { playlistCheckRepository.save(any()) } just runs
        every { dashboardRefresh.notifyUserPlaylistChecks(userId) } just runs
        every { notification.notifyViolationsChanged(any()) } just runs

        val result = adapter.handle(event)

        assertThat(result.isRight()).isTrue()
        verify(exactly = 0) { notification.notifyCheckPassed(any()) }
        verify(exactly = 1) { notification.notifyViolationsChanged(any()) }
    }

    @Test
    fun `handle does not send notification when check stays failed with same violations`() {
        val playlist = buildPlaylist(
            listOf(
                buildTrack("t1"),
                buildTrack("t1"),
            ),
        )
        val appTrackT1 = buildAppTrack("t1", "Track A", "Artist A")
        val previousCheck = buildCheck(succeeded = false, violations = listOf("Artist A – Track A"))
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"))) } returns listOf(appTrackT1)
        every { playlistCheckRepository.findByCheckId(checkId) } returns previousCheck
        every { playlistCheckRepository.save(any()) } just runs
        every { dashboardRefresh.notifyUserPlaylistChecks(userId) } just runs

        val result = adapter.handle(event)

        assertThat(result.isRight()).isTrue()
        verify(exactly = 0) { notification.notifyCheckPassed(any()) }
        verify(exactly = 0) { notification.notifyViolationsChanged(any()) }
    }

    @Test
    fun `handle does not send notification when check stays passed`() {
        val playlist = buildPlaylist(listOf(buildTrack("t1"), buildTrack("t2")))
        val check = buildCheck(succeeded = true)
        val previousCheck = buildCheck(succeeded = true)
        setupCheckRunner(check)
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { appTrackRepository.findByTrackIds(any()) } returns emptyList()
        every { playlistCheckRepository.findByCheckId(checkId) } returns previousCheck
        every { playlistCheckRepository.save(any()) } just runs
        every { dashboardRefresh.notifyUserPlaylistChecks(userId) } just runs

        val result = adapter.handle(event)

        assertThat(result.isRight()).isTrue()
        verify(exactly = 0) { notification.notifyCheckPassed(any()) }
        verify(exactly = 0) { notification.notifyViolationsChanged(any()) }
    }

    @Test
    fun `handle propagates unexpected exception`() {
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } throws RuntimeException("DB error")

        org.assertj.core.api.Assertions.assertThatThrownBy { adapter.handle(event) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("DB error")
    }

    @Test
    fun `handle skips inapplicable checks`() {
        val playlist = buildPlaylist(listOf(buildTrack("t1")))
        every { checkRunner.isApplicable(any()) } returns false
        every { checkRunners.iterator() } answers { mutableListOf(checkRunner).iterator() }
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo())
        every { dashboardRefresh.notifyUserPlaylistChecks(userId) } just runs

        val result = adapter.handle(event)

        assertThat(result.isRight()).isTrue()
        verify(exactly = 0) { playlistCheckRepository.save(any()) }
    }

    @Test
    fun `getDisplayNames returns map from all runners`() {
        every { checkRunners.iterator() } returns mutableListOf(checkRunner).iterator()
        every { checkRunner.checkId } returns checkId
        every { checkRunner.displayName } returns "Test Check"

        val names = adapter.getDisplayNames()

        assertThat(names).containsEntry(checkId, "Test Check")
    }

    @Test
    fun `handle throws when duplicate track not found in catalog`() {
        val playlist = buildPlaylist(
            listOf(
                buildTrack("t1"),
                buildTrack("t1"),
            ),
        )
        every { playlistRepository.findByUserIdAndPlaylistId(userId, playlistId) } returns playlist
        every { appTrackRepository.findByTrackIds(setOf(TrackId("t1"))) } returns emptyList()

        org.assertj.core.api.Assertions.assertThatThrownBy { adapter.handle(event) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("t1")
    }
}
