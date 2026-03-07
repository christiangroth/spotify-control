package de.chrgroth.spotify.control.domain

import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.AuthError
import de.chrgroth.spotify.control.domain.error.EnrichmentError
import de.chrgroth.spotify.control.domain.error.PlaybackError
import de.chrgroth.spotify.control.domain.error.PlaylistSyncError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.CurrentlyPlayingPort
import de.chrgroth.spotify.control.domain.port.`in`.PlaybackDataPort
import de.chrgroth.spotify.control.domain.port.`in`.PlaybackEnrichmentPort
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

    private val currentlyPlaying: CurrentlyPlayingPort = mockk()
    private val recentlyPlayed: RecentlyPlayedPort = mockk()
    private val userProfileUpdate: UserProfileUpdatePort = mockk()
    private val playlistSync: PlaylistSyncPort = mockk()
    private val playbackData: PlaybackDataPort = mockk()
    private val playbackEnrichment: PlaybackEnrichmentPort = mockk()

    private val adapter = OutboxHandlerAdapter(currentlyPlaying, recentlyPlayed, userProfileUpdate, playlistSync, playbackData, playbackEnrichment)

    private val userId = UserId("user-1")
    private val currentlyPlayingEvent = DomainOutboxEvent.FetchCurrentlyPlaying(userId)
    private val fetchEvent = DomainOutboxEvent.FetchRecentlyPlayed(userId)
    private val updateEvent = DomainOutboxEvent.UpdateUserProfile(userId)
    private val syncEvent = DomainOutboxEvent.SyncPlaylistInfo(userId)

    @Test
    fun `handle FetchCurrentlyPlaying delegates to CurrentlyPlayingPort successfully`() {
        every { currentlyPlaying.update(userId) } returns Unit.right()

        val result = adapter.handle(currentlyPlayingEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Success::class.java)
        verify { currentlyPlaying.update(userId) }
    }

    @Test
    fun `handle FetchCurrentlyPlaying returns Failed on domain error`() {
        every { currentlyPlaying.update(userId) } returns PlaybackError.CURRENTLY_PLAYING_FETCH_FAILED.left()

        val result = adapter.handle(currentlyPlayingEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Failed::class.java)
        assertThat((result as OutboxTaskResult.Failed).message).contains(PlaybackError.CURRENTLY_PLAYING_FETCH_FAILED.code)
    }

    @Test
    fun `handle FetchCurrentlyPlaying returns RateLimited on SpotifyRateLimitError`() {
        val retryAfter = Duration.ofSeconds(30)
        every { currentlyPlaying.update(userId) } returns SpotifyRateLimitError(retryAfter).left()

        val result = adapter.handle(currentlyPlayingEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.RateLimited::class.java)
        assertThat((result as OutboxTaskResult.RateLimited).retryAfter).isEqualTo(retryAfter)
    }

    @Test
    fun `handle FetchCurrentlyPlaying returns Failed on unexpected exception`() {
        every { currentlyPlaying.update(userId) } throws RuntimeException("connection error")

        val result = adapter.handle(currentlyPlayingEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Failed::class.java)
    }

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

    private val rebuildEvent = DomainOutboxEvent.RebuildPlaybackData(userId)

    @Test
    fun `handle RebuildPlaybackData delegates to PlaybackDataPort successfully`() {
        every { playbackData.rebuildPlaybackData(userId) } returns Unit

        val result = adapter.handle(rebuildEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Success::class.java)
        verify { playbackData.rebuildPlaybackData(userId) }
    }

    @Test
    fun `handle RebuildPlaybackData returns Failed on unexpected exception`() {
        every { playbackData.rebuildPlaybackData(userId) } throws RuntimeException("db error")

        val result = adapter.handle(rebuildEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Failed::class.java)
    }

    private val appendEvent = DomainOutboxEvent.AppendPlaybackData(userId)

    @Test
    fun `handle AppendPlaybackData delegates to PlaybackDataPort successfully`() {
        every { playbackData.appendPlaybackData(userId) } returns Unit

        val result = adapter.handle(appendEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Success::class.java)
        verify { playbackData.appendPlaybackData(userId) }
    }

    @Test
    fun `handle AppendPlaybackData returns Failed on unexpected exception`() {
        every { playbackData.appendPlaybackData(userId) } throws RuntimeException("db error")

        val result = adapter.handle(appendEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Failed::class.java)
    }

    private val artistId = "artist-1"
    private val trackId = "track-1"
    private val enrichArtistEvent = DomainOutboxEvent.EnrichArtistDetails(artistId, userId)

    @Test
    fun `handle EnrichArtistDetails delegates to PlaybackEnrichmentPort successfully`() {
        every { playbackEnrichment.enrichArtistDetails(artistId, userId) } returns Unit.right()

        val result = adapter.handle(enrichArtistEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Success::class.java)
        verify { playbackEnrichment.enrichArtistDetails(artistId, userId) }
    }

    @Test
    fun `handle EnrichArtistDetails returns RateLimited on rate limit error`() {
        val retryAfter = Duration.ofSeconds(30)
        every { playbackEnrichment.enrichArtistDetails(artistId, userId) } returns SpotifyRateLimitError(retryAfter).left()

        val result = adapter.handle(enrichArtistEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.RateLimited::class.java)
    }

    @Test
    fun `handle EnrichArtistDetails returns Failed on domain error`() {
        every { playbackEnrichment.enrichArtistDetails(artistId, userId) } returns EnrichmentError.ARTIST_DETAILS_FETCH_FAILED.left()

        val result = adapter.handle(enrichArtistEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Failed::class.java)
    }

    @Test
    fun `handle EnrichArtistDetails returns Failed on unexpected exception`() {
        every { playbackEnrichment.enrichArtistDetails(artistId, userId) } throws RuntimeException("api error")

        val result = adapter.handle(enrichArtistEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Failed::class.java)
    }

    private val enrichTrackEvent = DomainOutboxEvent.EnrichTrackDetails(trackId, userId)

    @Test
    fun `handle EnrichTrackDetails delegates to PlaybackEnrichmentPort successfully`() {
        every { playbackEnrichment.enrichTrackDetails(trackId, userId) } returns Unit.right()

        val result = adapter.handle(enrichTrackEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Success::class.java)
        verify { playbackEnrichment.enrichTrackDetails(trackId, userId) }
    }

    @Test
    fun `handle EnrichTrackDetails returns RateLimited on rate limit error`() {
        val retryAfter = Duration.ofSeconds(30)
        every { playbackEnrichment.enrichTrackDetails(trackId, userId) } returns SpotifyRateLimitError(retryAfter).left()

        val result = adapter.handle(enrichTrackEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.RateLimited::class.java)
    }

    @Test
    fun `handle EnrichTrackDetails returns Failed on domain error`() {
        every { playbackEnrichment.enrichTrackDetails(trackId, userId) } returns EnrichmentError.TRACK_DETAILS_FETCH_FAILED.left()

        val result = adapter.handle(enrichTrackEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Failed::class.java)
    }

    @Test
    fun `handle EnrichTrackDetails returns Failed on unexpected exception`() {
        every { playbackEnrichment.enrichTrackDetails(trackId, userId) } throws RuntimeException("api error")

        val result = adapter.handle(enrichTrackEvent)

        assertThat(result).isInstanceOf(OutboxTaskResult.Failed::class.java)
    }
}
