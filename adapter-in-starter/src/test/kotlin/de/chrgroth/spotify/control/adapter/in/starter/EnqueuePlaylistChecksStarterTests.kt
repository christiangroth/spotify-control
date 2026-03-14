package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.spotify.control.domain.model.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EnqueuePlaylistChecksStarterTests {

    private val userRepository: UserRepositoryPort = mockk()
    private val playlistRepository: PlaylistRepositoryPort = mockk()
    private val outboxPort: OutboxPort = mockk()

    private val starter = EnqueuePlaylistChecksStarter(userRepository, playlistRepository, outboxPort)

    private val userId = UserId("user-1")
    private val user = User(
        spotifyUserId = userId,
        displayName = "Test User",
        encryptedAccessToken = "enc-access",
        encryptedRefreshToken = "enc-refresh",
        tokenExpiresAt = Instant.fromEpochMilliseconds(0),
        lastLoginAt = Instant.fromEpochMilliseconds(0),
    )

    @BeforeEach
    fun setUp() {
        every { userRepository.findAll() } returns listOf(user)
        justRun { outboxPort.enqueue(any()) }
    }

    @Test
    fun `id is stable`() {
        assertThat(starter.id).isEqualTo("EnqueuePlaylistChecksStarter-v1")
    }

    @Test
    fun `no users - no events enqueued`() {
        every { userRepository.findAll() } returns emptyList()

        starter.execute()

        verify(exactly = 0) { outboxPort.enqueue(any()) }
    }

    @Test
    fun `no playlists - no events enqueued`() {
        every { playlistRepository.findByUserId(userId) } returns emptyList()

        starter.execute()

        verify(exactly = 0) { outboxPort.enqueue(any()) }
    }

    @Test
    fun `only passive playlists - no events enqueued`() {
        every { playlistRepository.findByUserId(userId) } returns listOf(
            playlistInfo("p1", PlaylistSyncStatus.PASSIVE),
        )

        starter.execute()

        verify(exactly = 0) { outboxPort.enqueue(any()) }
    }

    @Test
    fun `one active playlist - one event enqueued`() {
        every { playlistRepository.findByUserId(userId) } returns listOf(
            playlistInfo("p1", PlaylistSyncStatus.ACTIVE),
        )

        starter.execute()

        verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.RunPlaylistChecks(userId, "p1")) }
    }

    @Test
    fun `mixed active and passive playlists - only active enqueued`() {
        every { playlistRepository.findByUserId(userId) } returns listOf(
            playlistInfo("p1", PlaylistSyncStatus.ACTIVE),
            playlistInfo("p2", PlaylistSyncStatus.PASSIVE),
            playlistInfo("p3", PlaylistSyncStatus.ACTIVE),
        )

        starter.execute()

        verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.RunPlaylistChecks(userId, "p1")) }
        verify(exactly = 0) { outboxPort.enqueue(DomainOutboxEvent.RunPlaylistChecks(userId, "p2")) }
        verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.RunPlaylistChecks(userId, "p3")) }
    }

    @Test
    fun `multiple users - active playlists of each enqueued`() {
        val userId2 = UserId("user-2")
        val user2 = user.copy(spotifyUserId = userId2)
        every { userRepository.findAll() } returns listOf(user, user2)
        every { playlistRepository.findByUserId(userId) } returns listOf(
            playlistInfo("p1", PlaylistSyncStatus.ACTIVE),
        )
        every { playlistRepository.findByUserId(userId2) } returns listOf(
            playlistInfo("p2", PlaylistSyncStatus.ACTIVE),
        )

        starter.execute()

        verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.RunPlaylistChecks(userId, "p1")) }
        verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.RunPlaylistChecks(userId2, "p2")) }
    }

    @Test
    fun `playlist repository throws - continues without throwing and skips user`() {
        val userId2 = UserId("user-2")
        val user2 = user.copy(spotifyUserId = userId2)
        every { userRepository.findAll() } returns listOf(user, user2)
        every { playlistRepository.findByUserId(userId) } throws RuntimeException("DB error")
        every { playlistRepository.findByUserId(userId2) } returns listOf(
            playlistInfo("p2", PlaylistSyncStatus.ACTIVE),
        )

        starter.execute()

        verify(exactly = 0) { outboxPort.enqueue(match { it is DomainOutboxEvent.RunPlaylistChecks && it.userId == userId }) }
        verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.RunPlaylistChecks(userId2, "p2")) }
    }

    private fun playlistInfo(playlistId: String, syncStatus: PlaylistSyncStatus) = PlaylistInfo(
        spotifyPlaylistId = playlistId,
        snapshotId = "snapshot-$playlistId",
        lastSnapshotIdSyncTime = Instant.fromEpochMilliseconds(0),
        name = "Playlist $playlistId",
        syncStatus = syncStatus,
    )
}
