package de.chrgroth.spotify.control.adapter.`in`.starter

import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.PlaylistSyncError
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.playlist.SpotifyPlaylistItem
import de.chrgroth.spotify.control.domain.model.user.User
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.playlist.SpotifyPlaylistPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RemoveNonOwnedPlaylistMetadataBugfixStarterTests {

    private val userRepository: UserRepositoryPort = mockk()
    private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
    private val spotifyPlaylist: SpotifyPlaylistPort = mockk()
    private val playlistRepository: PlaylistRepositoryPort = mockk()

    private val starter = RemoveNonOwnedPlaylistMetadataBugfixStarter(
        userRepository,
        spotifyAccessToken,
        spotifyPlaylist,
        playlistRepository,
    )

    private val userId = UserId("user1")
    private val accessToken = AccessToken("token")
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
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
    }

    @Test
    fun `id is stable`() {
        assertThat(starter.id).isEqualTo("RemoveNonOwnedPlaylistMetadata-v1")
    }

    @Test
    fun `no users - execute runs without throwing`() {
        every { userRepository.findAll() } returns emptyList()

        starter.execute()
    }

    @Test
    fun `no non-owned playlists - no saveAll called`() {
        val ownedPlaylist = spotifyPlaylistItem("playlist1", userId.value)
        every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(ownedPlaylist).right()
        every { playlistRepository.findByUserId(userId) } returns listOf(playlistInfo("playlist1"))

        starter.execute()

        verify(exactly = 0) { playlistRepository.saveAll(any(), any()) }
    }

    @Test
    fun `non-owned playlist exists - removed via saveAll`() {
        val ownedPlaylist = spotifyPlaylistItem("playlist1", userId.value)
        val nonOwnedPlaylist = spotifyPlaylistItem("playlist2", "other-user")
        every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(ownedPlaylist, nonOwnedPlaylist).right()
        val ownedInfo = playlistInfo("playlist1")
        val nonOwnedInfo = playlistInfo("playlist2")
        every { playlistRepository.findByUserId(userId) } returns listOf(ownedInfo, nonOwnedInfo)
        justRun { playlistRepository.saveAll(userId, listOf(ownedInfo)) }

        starter.execute()

        verify(exactly = 1) { playlistRepository.saveAll(userId, listOf(ownedInfo)) }
    }

    @Test
    fun `all playlists non-owned - all removed via saveAll`() {
        val nonOwnedPlaylist = spotifyPlaylistItem("playlist1", "other-user")
        every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(nonOwnedPlaylist).right()
        val nonOwnedInfo = playlistInfo("playlist1")
        every { playlistRepository.findByUserId(userId) } returns listOf(nonOwnedInfo)
        justRun { playlistRepository.saveAll(userId, emptyList()) }

        starter.execute()

        verify(exactly = 1) { playlistRepository.saveAll(userId, emptyList()) }
    }

    @Test
    fun `spotify api returns error - no changes made`() {
        every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns PlaylistSyncError.PLAYLIST_FETCH_FAILED.left()

        starter.execute()

        verify(exactly = 0) { playlistRepository.findByUserId(any()) }
        verify(exactly = 0) { playlistRepository.saveAll(any(), any()) }
    }

    @Test
    fun `access token throws - execute continues without throwing`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } throws RuntimeException("Token error")

        starter.execute()

        verify(exactly = 0) { playlistRepository.saveAll(any(), any()) }
    }

    @Test
    fun `multiple users - each processed independently`() {
        val userId2 = UserId("user2")
        val user2 = user.copy(spotifyUserId = userId2)
        val accessToken2 = AccessToken("token2")
        every { userRepository.findAll() } returns listOf(user, user2)
        every { spotifyAccessToken.getValidAccessToken(userId2) } returns accessToken2

        val ownedPlaylist1 = spotifyPlaylistItem("playlist1", userId.value)
        every { spotifyPlaylist.getPlaylists(userId, accessToken) } returns listOf(ownedPlaylist1).right()
        every { playlistRepository.findByUserId(userId) } returns listOf(playlistInfo("playlist1"))

        val nonOwnedForUser2 = spotifyPlaylistItem("playlist2", "other-user")
        every { spotifyPlaylist.getPlaylists(userId2, accessToken2) } returns listOf(nonOwnedForUser2).right()
        val nonOwnedInfo2 = playlistInfo("playlist2")
        every { playlistRepository.findByUserId(userId2) } returns listOf(nonOwnedInfo2)
        justRun { playlistRepository.saveAll(userId2, emptyList()) }

        starter.execute()

        verify(exactly = 0) { playlistRepository.saveAll(userId, any()) }
        verify(exactly = 1) { playlistRepository.saveAll(userId2, emptyList()) }
    }

    private fun spotifyPlaylistItem(id: String, ownerId: String) = SpotifyPlaylistItem(
        id = id,
        name = "Playlist $id",
        snapshotId = "snapshot-$id",
        ownerId = ownerId,
    )

    private fun playlistInfo(playlistId: String) = PlaylistInfo(
        spotifyPlaylistId = playlistId,
        snapshotId = "snapshot-$playlistId",
        lastSnapshotIdSyncTime = Instant.fromEpochMilliseconds(0),
        name = "Playlist $playlistId",
        syncStatus = PlaylistSyncStatus.PASSIVE,
    )
}
