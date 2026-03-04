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
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
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
    private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
    private val spotifyPlaylist: SpotifyPlaylistPort = mockk()
    private val outboxPort: OutboxPort = mockk()

    private val adapter = PlaylistSyncAdapter(userRepository, spotifyAccessToken, spotifyPlaylist, outboxPort)

    private val userId = UserId("user-1")
    private val accessToken = AccessToken("access-token")
    private val now = Clock.System.now()

    private fun buildUser(id: String = "user-1", playlists: List<PlaylistInfo> = emptyList()) = User(
        spotifyUserId = UserId(id),
        displayName = "User $id",
        encryptedAccessToken = "enc-access",
        encryptedRefreshToken = "enc-refresh",
        tokenExpiresAt = now + 1.hours,
        lastLoginAt = now,
        playlists = playlists,
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
    fun `syncPlaylists persists new playlists with ACTIVE status`() {
        val user = buildUser()
        every { userRepository.findById(userId) } returns user
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(buildSpotifyItem("p1")).right()
        every { userRepository.upsert(any()) } just runs

        val result = adapter.syncPlaylists(userId)

        assertThat(result.isRight()).isTrue()
        val upsertedSlot = slot<User>()
        verify { userRepository.upsert(capture(upsertedSlot)) }
        assertThat(upsertedSlot.captured.playlists).hasSize(1)
        assertThat(upsertedSlot.captured.playlists[0].spotifyPlaylistId).isEqualTo("p1")
        assertThat(upsertedSlot.captured.playlists[0].syncStatus).isEqualTo(PlaylistSyncStatus.ACTIVE)
    }

    @Test
    fun `syncPlaylists preserves existing syncStatus`() {
        val user = buildUser(playlists = listOf(buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.PASSIVE)))
        every { userRepository.findById(userId) } returns user
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(buildSpotifyItem("p1")).right()
        every { userRepository.upsert(any()) } just runs

        adapter.syncPlaylists(userId)

        val upsertedSlot = slot<User>()
        verify { userRepository.upsert(capture(upsertedSlot)) }
        assertThat(upsertedSlot.captured.playlists[0].syncStatus).isEqualTo(PlaylistSyncStatus.PASSIVE)
    }

    @Test
    fun `syncPlaylists returns Left when spotify fetch fails`() {
        val user = buildUser()
        every { userRepository.findById(userId) } returns user
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns PlaylistSyncError.PLAYLIST_FETCH_FAILED.left()

        val result = adapter.syncPlaylists(userId)

        assertThat(result.isLeft()).isTrue()
        verify(exactly = 0) { userRepository.upsert(any()) }
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
        val user = buildUser(playlists = listOf(buildPlaylistInfo("p1")))
        every { userRepository.findById(userId) } returns user

        val result = adapter.updateSyncStatus(userId, "p-unknown", PlaylistSyncStatus.PASSIVE)

        assertThat(result.isLeft()).isTrue()
        assertThat(result.leftOrNull()).isEqualTo(PlaylistSyncError.PLAYLIST_NOT_FOUND)
    }

    @Test
    fun `updateSyncStatus updates only the target playlist`() {
        val user = buildUser(
            playlists = listOf(
                buildPlaylistInfo("p1", syncStatus = PlaylistSyncStatus.ACTIVE),
                buildPlaylistInfo("p2", syncStatus = PlaylistSyncStatus.ACTIVE),
            ),
        )
        every { userRepository.findById(userId) } returns user
        every { userRepository.upsert(any()) } just runs

        val result = adapter.updateSyncStatus(userId, "p1", PlaylistSyncStatus.PASSIVE)

        assertThat(result.isRight()).isTrue()
        val upsertedSlot = slot<User>()
        verify { userRepository.upsert(capture(upsertedSlot)) }
        val updated = upsertedSlot.captured.playlists.associateBy { it.spotifyPlaylistId }
        assertThat(updated["p1"]!!.syncStatus).isEqualTo(PlaylistSyncStatus.PASSIVE)
        assertThat(updated["p2"]!!.syncStatus).isEqualTo(PlaylistSyncStatus.ACTIVE)
    }
}
