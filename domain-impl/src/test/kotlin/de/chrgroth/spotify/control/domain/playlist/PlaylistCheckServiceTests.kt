package de.chrgroth.spotify.control.domain.playlist

import arrow.core.right
import de.chrgroth.spotify.control.domain.playlist.check.PlaylistCheckRunner
import de.chrgroth.spotify.control.domain.error.PlaylistFixError
import de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckViolation
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistId
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.User
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistCheckNotificationPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import de.chrgroth.spotify.control.domain.user.CurrentUserResolver
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import jakarta.enterprise.inject.Instance
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.microprofile.context.ManagedExecutor
import org.junit.jupiter.api.Test
import kotlin.time.Clock

class PlaylistCheckServiceTests {

  private val checkRunner: PlaylistCheckRunner = mockk()
  private val checkRunners: Instance<PlaylistCheckRunner> = mockk()
  private val playlistRepository: PlaylistRepositoryPort = mockk()
  private val playlistCheckRepository: AppPlaylistCheckRepositoryPort = mockk()
  private val dashboardRefresh: DashboardRefreshPort = mockk()
  private val notification: PlaylistCheckNotificationPort = mockk()
  private val userRepository: UserRepositoryPort = mockk()
  private val currentUserResolver: CurrentUserResolver = mockk()
  private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
  private val outboxPort: OutboxPort = mockk()
  private val meterRegistry = SimpleMeterRegistry()
  private val managedExecutor: ManagedExecutor = mockk {
    every { supplyAsync(any<Supplier<Any>>()) } answers {
      CompletableFuture.completedFuture(firstArg<Supplier<Any>>().get())
    }
  }

  private val adapter = PlaylistCheckService(
    checkRunners,
    playlistRepository,
    playlistCheckRepository,
    userRepository,
    currentUserResolver,
    dashboardRefresh,
    notification,
    spotifyAccessToken,
    outboxPort,
    meterRegistry,
    managedExecutor,
  )

  private val userId = UserId("user-1")
  private val playlistId = "playlist-1"
  private val event = DomainOutboxEvent.RunPlaylistChecks(playlistId)
  private val checkId = "test-check"
  private val fullCheckId = "$playlistId:$checkId"

  private fun buildTrack(trackId: String) = PlaylistTrack(
    trackId = TrackId(trackId),
    artistIds = listOf(ArtistId("artist-1")),
    albumId = AlbumId("album-1"),
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

  private fun buildCheck(succeeded: Boolean, violations: List<PlaylistCheckViolation> = emptyList()) = AppPlaylistCheck(
    checkId = fullCheckId,
    playlistId = PlaylistId(playlistId),
    lastCheck = Clock.System.now(),
    succeeded = succeeded,
    violations = violations,
  )

  private fun setupCheckRunner(check: AppPlaylistCheck) {
    every { checkRunner.checkId } returns checkId
    every { checkRunner.displayName } returns "Test Check"
    every { checkRunner.isApplicable(any()) } returns true
    every { checkRunner.run(any(), any(), any(), any()) } returns check
    every { checkRunners.iterator() } answers { mutableListOf(checkRunner).iterator() }
  }

  @Test
  fun `handle returns success and skips notifications when playlist not found`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findByPlaylistId(playlistId) } returns null

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
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findByPlaylistId(playlistId) } returns playlist
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo())
    every { playlistCheckRepository.findByCheckId(fullCheckId) } returns null
    every { playlistCheckRepository.save(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistChecks() } just runs

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
    val previousCheck = buildCheck(succeeded = false, violations = listOf(PlaylistCheckViolation("t1", "Artist – Track t1")))
    setupCheckRunner(check)
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findByPlaylistId(playlistId) } returns playlist
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo())
    every { playlistCheckRepository.findByCheckId(fullCheckId) } returns previousCheck
    every { playlistCheckRepository.save(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistChecks() } just runs
    every { notification.notifyCheckPassed(any()) } just runs

    val result = adapter.handle(event)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { notification.notifyCheckPassed(any()) }
    verify(exactly = 0) { notification.notifyViolationsChanged(any()) }
  }

  @Test
  fun `handle sends notifyViolationsChanged when check stays failed with different violations`() {
    val playlist = buildPlaylist(listOf(buildTrack("t1")))
    val check = buildCheck(
      succeeded = false,
      violations = listOf(PlaylistCheckViolation("a", "Artist A – Track A"), PlaylistCheckViolation("b", "Artist B – Track B")),
    )
    val previousCheck = buildCheck(succeeded = false, violations = listOf(PlaylistCheckViolation("a", "Artist A – Track A")))
    setupCheckRunner(check)
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findByPlaylistId(playlistId) } returns playlist
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo())
    every { playlistCheckRepository.findByCheckId(fullCheckId) } returns previousCheck
    every { playlistCheckRepository.save(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistChecks() } just runs
    every { notification.notifyViolationsChanged(any()) } just runs

    val result = adapter.handle(event)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { notification.notifyCheckPassed(any()) }
    verify(exactly = 1) { notification.notifyViolationsChanged(any()) }
  }

  @Test
  fun `handle does not send notification when check stays failed with same violations`() {
    val playlist = buildPlaylist(listOf(buildTrack("t1")))
    val violations = listOf(PlaylistCheckViolation("a", "Artist A – Track A"))
    val check = buildCheck(succeeded = false, violations = violations)
    val previousCheck = buildCheck(succeeded = false, violations = violations)
    setupCheckRunner(check)
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findByPlaylistId(playlistId) } returns playlist
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo())
    every { playlistCheckRepository.findByCheckId(fullCheckId) } returns previousCheck
    every { playlistCheckRepository.save(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistChecks() } just runs

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
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findByPlaylistId(playlistId) } returns playlist
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo())
    every { playlistCheckRepository.findByCheckId(fullCheckId) } returns previousCheck
    every { playlistCheckRepository.save(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistChecks() } just runs

    val result = adapter.handle(event)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { notification.notifyCheckPassed(any()) }
    verify(exactly = 0) { notification.notifyViolationsChanged(any()) }
  }

  @Test
  fun `handle propagates unexpected exception`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findByPlaylistId(playlistId) } throws RuntimeException("DB error")

    org.assertj.core.api.Assertions.assertThatThrownBy { adapter.handle(event) }
      .isInstanceOf(RuntimeException::class.java)
      .hasMessage("DB error")
  }

  @Test
  fun `handle skips inapplicable checks`() {
    val playlist = buildPlaylist(listOf(buildTrack("t1")))
    every { checkRunner.isApplicable(any()) } returns false
    every { checkRunners.iterator() } answers { mutableListOf(checkRunner).iterator() }
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findByPlaylistId(playlistId) } returns playlist
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo())
    every { dashboardRefresh.notifyUserPlaylistChecks() } just runs

    val result = adapter.handle(event)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { playlistCheckRepository.save(any()) }
  }

  @Test
  fun `getCheckDashboard returns dashboard with user display name, checks and metadata`() {
    val user = User(
      spotifyUserId = userId,
      displayName = "John Doe",
      encryptedAccessToken = "",
      encryptedRefreshToken = "",
      tokenExpiresAt = Clock.System.now(),
      lastLoginAt = Clock.System.now(),
    )
    val check = buildCheck(succeeded = true)
    val playlistInfo = buildPlaylistInfo()
    every { userRepository.get() } returns user
    every { playlistRepository.findAll() } returns listOf(playlistInfo)
    every { playlistCheckRepository.findAll() } returns listOf(check)
    every { checkRunners.iterator() } answers { mutableListOf(checkRunner).iterator() }
    every { checkRunner.checkId } returns checkId
    every { checkRunner.displayName } returns "Test Check"
    every { checkRunner.canFix() } returns true

    val dashboard = adapter.getCheckDashboard()

    assertThat(dashboard.displayName).isEqualTo("John Doe")
    assertThat(dashboard.checks).containsExactly(check)
    assertThat(dashboard.playlistNameById).containsEntry(playlistId, "Playlist $playlistId")
    assertThat(dashboard.displayNames).containsEntry(checkId, "Test Check")
    assertThat(dashboard.fixableCheckIds).containsExactly(checkId)
  }

  @Test
  fun `getCheckDashboard falls back to empty display name but still returns checks when no user exists`() {
    val check = buildCheck(succeeded = true)
    val playlistInfo = buildPlaylistInfo()
    every { userRepository.get() } returns null
    every { playlistRepository.findAll() } returns listOf(playlistInfo)
    every { playlistCheckRepository.findAll() } returns listOf(check)
    every { checkRunners.iterator() } answers { mutableListOf<PlaylistCheckRunner>().iterator() }

    val dashboard = adapter.getCheckDashboard()

    assertThat(dashboard.displayName).isEqualTo("")
    assertThat(dashboard.checks).containsExactly(check)
    assertThat(dashboard.playlistNameById).containsEntry(playlistId, "Playlist $playlistId")
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
  fun `getFixableCheckIds returns check ids from runners that can fix`() {
    every { checkRunners.iterator() } returns mutableListOf(checkRunner).iterator()
    every { checkRunner.checkId } returns checkId
    every { checkRunner.canFix() } returns true

    val fixableIds = adapter.getFixableCheckIds()

    assertThat(fixableIds).containsExactly(checkId)
  }

  @Test
  fun `getFixableCheckIds excludes runners that cannot fix`() {
    every { checkRunners.iterator() } returns mutableListOf(checkRunner).iterator()
    every { checkRunner.checkId } returns checkId
    every { checkRunner.canFix() } returns false

    val fixableIds = adapter.getFixableCheckIds()

    assertThat(fixableIds).isEmpty()
  }

  @Test
  fun `enqueueFix returns error when no violations selected`() {
    every { currentUserResolver.userId() } returns userId

    val result = adapter.enqueueFix(playlistId, checkId, emptySet())

    assertThat(result.isLeft()).isTrue()
    assertThat((result as arrow.core.Either.Left).value).isEqualTo(PlaylistFixError.NO_VIOLATIONS_SELECTED)
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `enqueueFix returns error when no runner with canFix found`() {
    every { currentUserResolver.userId() } returns userId
    every { checkRunners.iterator() } returns mutableListOf<PlaylistCheckRunner>().iterator()

    val result = adapter.enqueueFix(playlistId, "unknown-check", setOf("t1"))

    assertThat(result.isLeft()).isTrue()
    assertThat((result as arrow.core.Either.Left).value).isEqualTo(PlaylistFixError.FIX_NOT_FOUND)
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `enqueueFix returns error when playlist not found`() {
    every { currentUserResolver.userId() } returns userId
    every { checkRunners.iterator() } returns mutableListOf(checkRunner).iterator()
    every { checkRunner.checkId } returns checkId
    every { checkRunner.canFix() } returns true
    every { playlistRepository.findByPlaylistId(playlistId) } returns null

    val result = adapter.enqueueFix(playlistId, checkId, setOf("t1"))

    assertThat(result.isLeft()).isTrue()
    assertThat((result as arrow.core.Either.Left).value).isEqualTo(PlaylistFixError.PLAYLIST_NOT_FOUND)
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `enqueueFix enqueues FixPlaylistCheck when runner and playlist exist`() {
    every { currentUserResolver.userId() } returns userId
    every { checkRunners.iterator() } returns mutableListOf(checkRunner).iterator()
    every { checkRunner.checkId } returns checkId
    every { checkRunner.canFix() } returns true
    every { playlistRepository.findByPlaylistId(playlistId) } returns buildPlaylist(listOf(buildTrack("t1")))
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.enqueueFix(playlistId, checkId, setOf("t1"))

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.FixPlaylistCheck(playlistId, checkId, setOf("t1"))) }
  }

  @Test
  fun `handle FixPlaylistCheck calls fix on runner and enqueues re-sync on success`() {
    val playlist = buildPlaylist(listOf(buildTrack("t1")))
    val playlistInfo = buildPlaylistInfo()
    every { currentUserResolver.userId() } returns userId
    every { checkRunners.iterator() } returns mutableListOf(checkRunner).iterator()
    every { checkRunner.checkId } returns checkId
    every { checkRunner.canFix() } returns true
    every { playlistRepository.findByPlaylistId(playlistId) } returns playlist
    every { playlistRepository.findAll() } returns listOf(playlistInfo)
    every { spotifyAccessToken.getValidAccessToken() } returns AccessToken("token")
    every { checkRunner.fix(AccessToken("token"), playlistId, playlist, playlistInfo, listOf(playlistInfo), setOf("t1")) } returns Unit.right()
    every { outboxPort.enqueue(any()) } just runs

    val result = adapter.handle(DomainOutboxEvent.FixPlaylistCheck(playlistId, checkId, setOf("t1")))

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(playlistId)) }
  }

  @Test
  fun `handle FixPlaylistCheck returns error when playlist not found`() {
    every { currentUserResolver.userId() } returns userId
    every { checkRunners.iterator() } returns mutableListOf(checkRunner).iterator()
    every { checkRunner.checkId } returns checkId
    every { checkRunner.canFix() } returns true
    every { playlistRepository.findByPlaylistId(playlistId) } returns null

    val result = adapter.handle(DomainOutboxEvent.FixPlaylistCheck(playlistId, checkId, setOf("t1")))

    assertThat(result.isLeft()).isTrue()
    assertThat((result as arrow.core.Either.Left).value).isEqualTo(PlaylistFixError.PLAYLIST_NOT_FOUND)
  }

  @Test
  fun `enqueueRunAllChecks enqueues RunPlaylistChecks for each active playlist only`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns listOf(
      buildPlaylistInfo(),
      buildPlaylistInfo().copy(spotifyPlaylistId = "playlist-2", syncStatus = PlaylistSyncStatus.PASSIVE),
      buildPlaylistInfo().copy(spotifyPlaylistId = "playlist-3"),
    )
    every { outboxPort.enqueue(any()) } just runs

    adapter.enqueueRunAllChecks()

    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.RunPlaylistChecks(playlistId)) }
    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.RunPlaylistChecks("playlist-3")) }
    verify(exactly = 0) { outboxPort.enqueue(DomainOutboxEvent.RunPlaylistChecks("playlist-2")) }
  }

  @Test
  fun `enqueueRunAllChecks does nothing when no current user`() {
    every { currentUserResolver.userId() } returns null

    adapter.enqueueRunAllChecks()

    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `enqueueRunCheck enqueues RunPlaylistChecks with checkType for each active playlist only`() {
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findAll() } returns listOf(
      buildPlaylistInfo(),
      buildPlaylistInfo().copy(spotifyPlaylistId = "playlist-2", syncStatus = PlaylistSyncStatus.PASSIVE),
      buildPlaylistInfo().copy(spotifyPlaylistId = "playlist-3"),
    )
    every { outboxPort.enqueue(any()) } just runs

    adapter.enqueueRunCheck(checkId)

    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.RunPlaylistChecks(playlistId, checkId)) }
    verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.RunPlaylistChecks("playlist-3", checkId)) }
    verify(exactly = 0) { outboxPort.enqueue(DomainOutboxEvent.RunPlaylistChecks("playlist-2", checkId)) }
  }

  @Test
  fun `enqueueRunCheck does nothing when no current user`() {
    every { currentUserResolver.userId() } returns null

    adapter.enqueueRunCheck(checkId)

    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `handle only runs the runner matching checkType when specified`() {
    val playlist = buildPlaylist(listOf(buildTrack("t1")))
    val check = buildCheck(succeeded = true)
    val otherRunner: PlaylistCheckRunner = mockk()
    setupCheckRunner(check)
    every { otherRunner.isApplicable(any()) } returns true
    every { otherRunner.checkId } returns "other-check"
    every { checkRunners.iterator() } answers { mutableListOf(checkRunner, otherRunner).iterator() }
    every { currentUserResolver.userId() } returns userId
    every { playlistRepository.findByPlaylistId(playlistId) } returns playlist
    every { playlistRepository.findAll() } returns listOf(buildPlaylistInfo())
    every { playlistCheckRepository.findByCheckId(fullCheckId) } returns null
    every { playlistCheckRepository.save(any()) } just runs
    every { dashboardRefresh.notifyUserPlaylistChecks() } just runs

    val result = adapter.handle(DomainOutboxEvent.RunPlaylistChecks(playlistId, checkId))

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { playlistCheckRepository.save(any()) }
    verify(exactly = 0) { otherRunner.run(any(), any(), any(), any()) }
  }
}
