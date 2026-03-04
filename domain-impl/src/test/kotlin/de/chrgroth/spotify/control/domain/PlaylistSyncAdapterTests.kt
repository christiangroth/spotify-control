package de.chrgroth.spotify.control.domain

import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.PlaylistSyncError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.SpotifyPlaylistItem
import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyPlaylistPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class PlaylistSyncAdapterTests {

    private val userRepository: UserRepositoryPort = mockk()
    private val playlistRepository: PlaylistRepositoryPort = mockk()
    private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
    private val spotifyPlaylist: SpotifyPlaylistPort = mockk()
    private val outboxPort: OutboxPort = mockk()
    private val dashboardRefresh: DashboardRefreshPort = mockk()

    private val adapter = PlaylistSyncAdapter(userRepository, playlistRepository, spotifyAccessToken, spotifyPlaylist, outboxPort, dashboardRefresh)

    private val userId = UserId("user-1")
    private val accessToken = AccessToken("access-token")
    private val now = Clock.System.now()

    private fun buildUser(id: String = "user-1") = User(
        spotifyUserId = UserId(id),
        displayName = "User $id",
        encryptedAccessToken = "enc-access",
        encryptedRefreshToken = "enc-refresh",
        tokenExpiresAt = now + 1.hours,
        lastLoginAt = now,
    )

    private fun buildPlaylistInfo(id: String, snapshotId: String = "snap-1", syncStatus: PlaylistSyncStatus = PlaylistSyncStatus.ACTIVE) = PlaylistInfo(
        spotifyPlaylistId = id,
        snapshotId = snapshotId,
        lastSnapshotIdSyncTime = now - 1.hours,
        name = "Playlist $id",
        syncStatus = syncStatus,
    )

    private fun buildSpotifyItem(id: String, snapshotId: String = "snap-1") = SpotifyPlaylistItem(
        id = id,
        name = "Playlist $id",
        snapshotId = snapshotId,
    )

    // --- enqueueUpdates tests ---

    @Test
    fun `enqueueUpdates does nothing when no users exist`() {
        every { userRepository.findAll() } returns emptyList()

        adapter.enqueueUpdates()

        verify(exactly = 0) { outboxPort.enqueue(any()) }
    }

    @Test
    fun `enqueueUpdates enqueues one task per user`() {
        every { userRepository.findAll() } returns listOf(buildUser("user-1"), buildUser("user-2"))
        every { outboxPort.enqueue(any()) } just runs

        adapter.enqueueUpdates()

        verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistInfo(UserId("user-1"))) }
        verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistInfo(UserId("user-2"))) }
    }

    // --- syncPlaylists tests ---

    @Test
    fun `syncPlaylists skips when user not found`() {
        every { userRepository.findById(userId) } returns null

        val result = adapter.syncPlaylists(userId)

        assertThat(result.isRight()).isTrue()
        verify(exactly = 0) { spotifyPlaylist.getPlaylists(any(), any()) }
    }

    @Test
    fun `syncPlaylists persists new playlists with PASSIVE status`() {
        val user = buildUser()
        every { userRepository.findById(userId) } returns user
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(buildSpotifyItem("p1")).right()
        every { playlistRepository.findByUserId(userId) } returns emptyList()
        every { playlistRepository.saveAll(any(), any()) } just runs
        every { dashboardRefresh.notifyUser(userId) } just runs

        val result = adapter.syncPlaylists(userId)

        assertThat(result.isRight()).isTrue()
        val savedSlot = slot<List<PlaylistInfo>>()
        verify { playlistRepository.saveAll(userId, capture(savedSlot)) }
        assertThat(savedSlot.captured).hasSize(1)
        assertThat(savedSlot.captured[0].spotifyPlaylistId).isEqualTo("p1")
        assertThat(savedSlot.captured[0].syncStatus).isEqualTo(PlaylistSyncStatus.PASSIVE)
    }

    @Test
    fun `syncPlaylists preserves existing syncStatus`() {
        val user = buildUser()
        every { userRepository.findById(userId) } returns user
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(buildSpotifyItem("p1")).right()
        every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.PASSIVE))
        every { playlistRepository.saveAll(any(), any()) } just runs

        adapter.syncPlaylists(userId)

        val savedSlot = slot<List<PlaylistInfo>>()
        verify { playlistRepository.saveAll(userId, capture(savedSlot)) }
        assertThat(savedSlot.captured[0].syncStatus).isEqualTo(PlaylistSyncStatus.PASSIVE)
    }

    @Test
    fun `syncPlaylists uses latest playlist state after Spotify API call to preserve syncStatus changes made in the meantime`() {
        val user = buildUser()
        every { userRepository.findById(userId) } returns user
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(buildSpotifyItem("p1")).right()
        every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.ACTIVE))
        every { playlistRepository.saveAll(any(), any()) } just runs

        adapter.syncPlaylists(userId)

        val savedSlot = slot<List<PlaylistInfo>>()
        verify { playlistRepository.saveAll(userId, capture(savedSlot)) }
        assertThat(savedSlot.captured[0].syncStatus).isEqualTo(PlaylistSyncStatus.ACTIVE)
    }

    @Test
    fun `syncPlaylists preserves lastSnapshotIdSyncTime when snapshotId is unchanged`() {
        val user = buildUser()
        every { userRepository.findById(userId) } returns user
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(buildSpotifyItem("p1", snapshotId = "snap-1")).right()
        every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1", snapshotId = "snap-1"))
        every { playlistRepository.saveAll(any(), any()) } just runs

        adapter.syncPlaylists(userId)

        val savedSlot = slot<List<PlaylistInfo>>()
        verify { playlistRepository.saveAll(userId, capture(savedSlot)) }
        assertThat(savedSlot.captured[0].lastSnapshotIdSyncTime).isEqualTo(now - 1.hours)
    }

    @Test
    fun `syncPlaylists updates lastSnapshotIdSyncTime when snapshotId changes`() {
        val user = buildUser()
        every { userRepository.findById(userId) } returns user
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(buildSpotifyItem("p1", snapshotId = "snap-2")).right()
        every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1", snapshotId = "snap-1"))
        every { playlistRepository.saveAll(any(), any()) } just runs

        adapter.syncPlaylists(userId)

        val savedSlot = slot<List<PlaylistInfo>>()
        verify { playlistRepository.saveAll(userId, capture(savedSlot)) }
        assertThat(savedSlot.captured[0].lastSnapshotIdSyncTime).isGreaterThan(now - 1.hours)
    }

    @Test
    fun `syncPlaylists notifies dashboard when playlist count increases`() {
        val user = buildUser()
        every { userRepository.findById(userId) } returns user
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(buildSpotifyItem("p1"), buildSpotifyItem("p2")).right()
        every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1"))
        every { playlistRepository.saveAll(any(), any()) } just runs
        every { dashboardRefresh.notifyUser(userId) } just runs

        val result = adapter.syncPlaylists(userId)

        assertThat(result.isRight()).isTrue()
        verify(exactly = 1) { dashboardRefresh.notifyUser(userId) }
    }

    @Test
    fun `syncPlaylists notifies dashboard when playlist count decreases`() {
        val user = buildUser()
        every { userRepository.findById(userId) } returns user
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(buildSpotifyItem("p1")).right()
        every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1"), buildPlaylistInfo("p2"))
        every { playlistRepository.saveAll(any(), any()) } just runs
        every { dashboardRefresh.notifyUser(userId) } just runs

        val result = adapter.syncPlaylists(userId)

        assertThat(result.isRight()).isTrue()
        verify(exactly = 1) { dashboardRefresh.notifyUser(userId) }
    }

    @Test
    fun `syncPlaylists does not notify dashboard when playlist count is unchanged`() {
        val user = buildUser()
        every { userRepository.findById(userId) } returns user
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(buildSpotifyItem("p1")).right()
        every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1"))
        every { playlistRepository.saveAll(any(), any()) } just runs

        val result = adapter.syncPlaylists(userId)

        assertThat(result.isRight()).isTrue()
        verify(exactly = 0) { dashboardRefresh.notifyUser(any()) }
    }

    @Test
    fun `syncPlaylists returns Left when spotify fetch fails`() {
        val user = buildUser()
        every { userRepository.findById(userId) } returns user
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns PlaylistSyncError.PLAYLIST_FETCH_FAILED.left()

        val result = adapter.syncPlaylists(userId)

        assertThat(result.isLeft()).isTrue()
        verify(exactly = 0) { playlistRepository.saveAll(any(), any()) }
    }

    @Test
    fun `syncPlaylists returns Left with SpotifyRateLimitError when rate limited`() {
        val user = buildUser()
        every { userRepository.findById(userId) } returns user
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns SpotifyRateLimitError(Duration.ofSeconds(30)).left()

        val result = adapter.syncPlaylists(userId)

        assertThat(result.isLeft()).isTrue()
        assertThat(result.leftOrNull()).isInstanceOf(SpotifyRateLimitError::class.java)
    }

    // --- updateSyncStatus tests ---

    @Test
    fun `updateSyncStatus returns Left when user not found`() {
        every { userRepository.findById(userId) } returns null

        val result = adapter.updateSyncStatus(userId, "p1", PlaylistSyncStatus.PASSIVE)

        assertThat(result.isLeft()).isTrue()
        assertThat(result.leftOrNull()).isEqualTo(PlaylistSyncError.PLAYLIST_NOT_FOUND)
    }

    @Test
    fun `updateSyncStatus returns Left when playlist not found`() {
        val user = buildUser()
        every { userRepository.findById(userId) } returns user
        every { playlistRepository.findByUserId(userId) } returns listOf(buildPlaylistInfo("p1"))

        val result = adapter.updateSyncStatus(userId, "p-unknown", PlaylistSyncStatus.PASSIVE)

        assertThat(result.isLeft()).isTrue()
        assertThat(result.leftOrNull()).isEqualTo(PlaylistSyncError.PLAYLIST_NOT_FOUND)
    }

    @Test
    fun `updateSyncStatus updates only the target playlist`() {
        val user = buildUser()
        every { userRepository.findById(userId) } returns user
        every { playlistRepository.findByUserId(userId) } returns listOf(
            buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.ACTIVE),
            buildPlaylistInfo("p2", syncStatus = PlaylistSyncStatus.ACTIVE),
        )
        every { playlistRepository.saveAll(any(), any()) } just runs

        val result = adapter.updateSyncStatus(userId, "p1", PlaylistSyncStatus.PASSIVE)

        assertThat(result.isRight()).isTrue()
        val savedSlot = slot<List<PlaylistInfo>>()
        verify { playlistRepository.saveAll(userId, capture(savedSlot)) }
        val updated = savedSlot.captured.associateBy { it.spotifyPlaylistId }
        assertThat(updated["p1"]!!.syncStatus).isEqualTo(PlaylistSyncStatus.PASSIVE)
        assertThat(updated["p2"]!!.syncStatus).isEqualTo(PlaylistSyncStatus.ACTIVE)
    }
}
