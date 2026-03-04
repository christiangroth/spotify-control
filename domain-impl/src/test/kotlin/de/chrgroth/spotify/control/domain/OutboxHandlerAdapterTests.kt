package de.chrgroth.spotify.control.domain

import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.AuthError
import de.chrgroth.spotify.control.domain.error.PlaybackError
import de.chrgroth.spotify.control.domain.error.PlaylistSyncError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.PlaylistSyncPort
import de.chrgroth.spotify.control.domain.port.`in`.RecentlyPlayedPort
import de.chrgroth.spotify.control.domain.port.`in`.UserProfileUpdatePort
import de.chrgroth.spotify.control.util.outbox.OutboxTaskResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class OutboxHandlerAdapterTests {

    private val recentlyPlayed: RecentlyPlayedPort = mockk()
    private val userProfileUpdate: UserProfileUpdatePort = mockk()
    private val playlistSync: PlaylistSyncPort = mockk()

    private val adapter = OutboxHandlerAdapter(recentlyPlayed, userProfileUpdate, playlistSync)

    private val userId = UserId("user-1")
    private val fetchEvent = DomainOutboxEvent.FetchRecentlyPlayed(userId)
    private val updateEvent = DomainOutboxEvent.UpdateUserProfile(userId)
    private val syncEvent = DomainOutboxEvent.SyncPlaylistInfo(userId)

    @Test
    fun `handle FetchRecentlyPlayed delegates to RecentlyPlayedPort successfully`() {
        every { recentlyPlayed.update(userId) } returns Unit.right()

        val result = adapter.handle(fetchEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Success::class.java)
        verify { recentlyPlayed.update(userId) }
    }

    @Test
    fun `handle FetchRecentlyPlayed returns Failed on domain error`() {
        every { recentlyPlayed.update(userId) } returns PlaybackError.RECENTLY_PLAYED_FETCH_FAILED.left()

        val result = adapter.handle(fetchEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Failed::class.java)
        assertThat((result as OutboxTaskResult.Failed).message).contains(PlaybackError.RECENTLY_PLAYED_FETCH_FAILED.code)
    }

    @Test
    fun `handle FetchRecentlyPlayed returns RateLimited on SpotifyRateLimitError`() {
        val retryAfter = Duration.ofSeconds(30)
        every { recentlyPlayed.update(userId) } returns SpotifyRateLimitError(retryAfter).left()

        val result = adapter.handle(fetchEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.RateLimited::class.java)
        assertThat((result as OutboxTaskResult.RateLimited).retryAfter).isEqualTo(retryAfter)
    }

    @Test
    fun `handle FetchRecentlyPlayed returns Failed on unexpected exception`() {
        every { recentlyPlayed.update(userId) } throws RuntimeException("connection error")

        val result = adapter.handle(fetchEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Failed::class.java)
    }

    @Test
    fun `handle UpdateUserProfile delegates to UserProfileUpdatePort successfully`() {
        every { userProfileUpdate.update(userId) } returns Unit.right()

        val result = adapter.handle(updateEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Success::class.java)
        verify { userProfileUpdate.update(userId) }
    }

    @Test
    fun `handle UpdateUserProfile returns Failed on domain error`() {
        every { userProfileUpdate.update(userId) } returns AuthError.PROFILE_FETCH_FAILED.left()

        val result = adapter.handle(updateEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Failed::class.java)
        assertThat((result as OutboxTaskResult.Failed).message).contains(AuthError.PROFILE_FETCH_FAILED.code)
    }

    @Test
    fun `handle UpdateUserProfile returns RateLimited on SpotifyRateLimitError`() {
        val retryAfter = Duration.ofSeconds(30)
        every { userProfileUpdate.update(userId) } returns SpotifyRateLimitError(retryAfter).left()

        val result = adapter.handle(updateEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.RateLimited::class.java)
        assertThat((result as OutboxTaskResult.RateLimited).retryAfter).isEqualTo(retryAfter)
    }

    @Test
    fun `handle UpdateUserProfile returns Failed on unexpected exception`() {
        every { userProfileUpdate.update(userId) } throws RuntimeException("connection error")

        val result = adapter.handle(updateEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Failed::class.java)
    }

    @Test
    fun `handle SyncPlaylistInfo delegates to PlaylistSyncPort successfully`() {
        every { playlistSync.syncPlaylists(userId) } returns Unit.right()

        val result = adapter.handle(syncEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Success::class.java)
        verify { playlistSync.syncPlaylists(userId) }
    }

    @Test
    fun `handle SyncPlaylistInfo returns Failed on domain error`() {
        every { playlistSync.syncPlaylists(userId) } returns PlaylistSyncError.PLAYLIST_FETCH_FAILED.left()

        val result = adapter.handle(syncEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Failed::class.java)
        assertThat((result as OutboxTaskResult.Failed).message).contains(PlaylistSyncError.PLAYLIST_FETCH_FAILED.code)
    }

    @Test
    fun `handle SyncPlaylistInfo returns RateLimited on SpotifyRateLimitError`() {
        val retryAfter = Duration.ofSeconds(30)
        every { playlistSync.syncPlaylists(userId) } returns SpotifyRateLimitError(retryAfter).left()

        val result = adapter.handle(syncEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.RateLimited::class.java)
        assertThat((result as OutboxTaskResult.RateLimited).retryAfter).isEqualTo(retryAfter)
    }

    @Test
    fun `handle SyncPlaylistInfo returns Failed on unexpected exception`() {
        every { playlistSync.syncPlaylists(userId) } throws RuntimeException("connection error")

        val result = adapter.handle(syncEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Failed::class.java)
    }

    private val syncPlaylistDataEvent = DomainOutboxEvent.SyncPlaylistData(userId, "playlist-1")

    @Test
    fun `handle SyncPlaylistData delegates to PlaylistSyncPort successfully`() {
        every { playlistSync.syncPlaylistData(userId, "playlist-1") } returns Unit.right()

        val result = adapter.handle(syncPlaylistDataEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Success::class.java)
        verify { playlistSync.syncPlaylistData(userId, "playlist-1") }
    }

    @Test
    fun `handle SyncPlaylistData returns Failed on domain error`() {
        every { playlistSync.syncPlaylistData(userId, "playlist-1") } returns PlaylistSyncError.PLAYLIST_TRACKS_FETCH_FAILED.left()

        val result = adapter.handle(syncPlaylistDataEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Failed::class.java)
        assertThat((result as OutboxTaskResult.Failed).message).contains(PlaylistSyncError.PLAYLIST_TRACKS_FETCH_FAILED.code)
    }

    @Test
    fun `handle SyncPlaylistData returns RateLimited on SpotifyRateLimitError`() {
        val retryAfter = Duration.ofSeconds(30)
        every { playlistSync.syncPlaylistData(userId, "playlist-1") } returns SpotifyRateLimitError(retryAfter).left()

        val result = adapter.handle(syncPlaylistDataEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.RateLimited::class.java)
        assertThat((result as OutboxTaskResult.RateLimited).retryAfter).isEqualTo(retryAfter)
    }

    @Test
    fun `handle SyncPlaylistData returns Failed on unexpected exception`() {
        every { playlistSync.syncPlaylistData(userId, "playlist-1") } throws RuntimeException("connection error")

        val result = adapter.handle(syncPlaylistDataEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Failed::class.java)
    }
}
