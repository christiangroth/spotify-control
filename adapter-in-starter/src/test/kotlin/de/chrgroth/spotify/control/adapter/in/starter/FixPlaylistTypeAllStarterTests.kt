package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.spotify.control.domain.model.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.PlaylistType
import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class FixPlaylistTypeAllStarterTests {

    private val userRepository: UserRepositoryPort = mockk()
    private val playlistRepository: PlaylistRepositoryPort = mockk()

    private val starter = FixPlaylistTypeAllStarter(userRepository, playlistRepository)

    private val userId = UserId("user-1")
    private val now = Clock.System.now()

    private fun buildUser() = User(
        spotifyUserId = userId,
        displayName = "User 1",
        encryptedAccessToken = "enc-access",
        encryptedRefreshToken = "enc-refresh",
        tokenExpiresAt = now + 1.hours,
        lastLoginAt = now,
    )

    private fun buildPlaylistInfo(
        id: String,
        name: String,
        syncStatus: PlaylistSyncStatus = PlaylistSyncStatus.ACTIVE,
        type: PlaylistType? = null,
    ) = PlaylistInfo(
        spotifyPlaylistId = id,
        snapshotId = "snap-1",
        lastSnapshotIdSyncTime = now - 1.hours,
        name = name,
        syncStatus = syncStatus,
        type = type,
    )

    @Test
    fun `id is stable`() {
        assertThat(starter.id).isEqualTo("FixPlaylistTypeAllStarter-v1")
    }

    @Test
    fun `execute sets type ALL for active playlist named All`() {
        every { userRepository.findAll() } returns listOf(buildUser())
        every { playlistRepository.findByUserId(userId) } returns listOf(
            buildPlaylistInfo("p1", "All"),
        )
        every { playlistRepository.saveAll(any(), any()) } just runs

        starter.execute()

        val savedSlot = slot<List<PlaylistInfo>>()
        verify { playlistRepository.saveAll(userId, capture(savedSlot)) }
        assertThat(savedSlot.captured.find { it.spotifyPlaylistId == "p1" }!!.type).isEqualTo(PlaylistType.ALL)
    }

    @Test
    fun `execute sets type ALL for active playlist named all case-insensitive`() {
        every { userRepository.findAll() } returns listOf(buildUser())
        every { playlistRepository.findByUserId(userId) } returns listOf(
            buildPlaylistInfo("p1", "ALL"),
        )
        every { playlistRepository.saveAll(any(), any()) } just runs

        starter.execute()

        val savedSlot = slot<List<PlaylistInfo>>()
        verify { playlistRepository.saveAll(userId, capture(savedSlot)) }
        assertThat(savedSlot.captured.find { it.spotifyPlaylistId == "p1" }!!.type).isEqualTo(PlaylistType.ALL)
    }

    @Test
    fun `execute does not change type for passive playlist named All`() {
        every { userRepository.findAll() } returns listOf(buildUser())
        every { playlistRepository.findByUserId(userId) } returns listOf(
            buildPlaylistInfo("p1", "All", syncStatus = PlaylistSyncStatus.PASSIVE),
        )

        starter.execute()

        verify(exactly = 0) { playlistRepository.saveAll(any(), any()) }
    }

    @Test
    fun `execute does not change type when already set to ALL`() {
        every { userRepository.findAll() } returns listOf(buildUser())
        every { playlistRepository.findByUserId(userId) } returns listOf(
            buildPlaylistInfo("p1", "All", type = PlaylistType.ALL),
        )

        starter.execute()

        verify(exactly = 0) { playlistRepository.saveAll(any(), any()) }
    }

    @Test
    fun `execute does not change other playlists`() {
        every { userRepository.findAll() } returns listOf(buildUser())
        every { playlistRepository.findByUserId(userId) } returns listOf(
            buildPlaylistInfo("p1", "All"),
            buildPlaylistInfo("p2", "My Playlist"),
        )
        every { playlistRepository.saveAll(any(), any()) } just runs

        starter.execute()

        val savedSlot = slot<List<PlaylistInfo>>()
        verify { playlistRepository.saveAll(userId, capture(savedSlot)) }
        assertThat(savedSlot.captured.find { it.spotifyPlaylistId == "p2" }!!.type).isNull()
    }

    @Test
    fun `execute does nothing when no users exist`() {
        every { userRepository.findAll() } returns emptyList()

        starter.execute()

        verify(exactly = 0) { playlistRepository.findByUserId(any()) }
        verify(exactly = 0) { playlistRepository.saveAll(any(), any()) }
    }
}
