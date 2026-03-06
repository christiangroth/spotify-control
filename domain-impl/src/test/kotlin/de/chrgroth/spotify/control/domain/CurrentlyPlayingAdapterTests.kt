package de.chrgroth.spotify.control.domain

import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.PlaybackError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.CurrentlyPlayingRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyCurrentlyPlayingPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CurrentlyPlayingAdapterTests {

    private val userRepository: UserRepositoryPort = mockk()
    private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
    private val spotifyCurrentlyPlaying: SpotifyCurrentlyPlayingPort = mockk()
    private val currentlyPlayingRepository: CurrentlyPlayingRepositoryPort = mockk()
    private val outboxPort: OutboxPort = mockk()
    private val dashboardRefresh: DashboardRefreshPort = mockk(relaxed = true)

    private val adapter = CurrentlyPlayingAdapter(
        userRepository,
        spotifyAccessToken,
        spotifyCurrentlyPlaying,
        currentlyPlayingRepository,
        outboxPort,
        dashboardRefresh,
    )

    private val userId = UserId("user-1")
    private val accessToken = AccessToken("token")
    private val now = Clock.System.now()

    private fun buildUser(id: String) = User(
        spotifyUserId = UserId(id),
        displayName = "User $id",
        encryptedAccessToken = "enc-access",
        encryptedRefreshToken = "enc-refresh",
        tokenExpiresAt = now + 1.hours,
        lastLoginAt = now,
    )

    private fun item() = CurrentlyPlayingItem(
        spotifyUserId = userId,
        trackId = "track-1",
        trackName = "Track 1",
        artistIds = listOf("artist-id-1"),
        artistNames = listOf("Artist 1"),
        progressMs = 45000L,
        durationMs = 200000L,
        isPlaying = true,
        observedAt = now,
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

        verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.FetchCurrentlyPlaying(UserId("user-1"))) }
        verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.FetchCurrentlyPlaying(UserId("user-2"))) }
    }

    // --- update tests ---

    @Test
    fun `update persists new currently playing item`() {
        val item = item()
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCurrentlyPlaying.getCurrentlyPlaying(userId, accessToken) } returns item.right()
        every { currentlyPlayingRepository.existsByUserAndTrackAndObservedMinute(item) } returns false
        every { currentlyPlayingRepository.save(item) } just runs

        adapter.update(userId)

        verify { currentlyPlayingRepository.save(item) }
    }

    @Test
    fun `update notifies playback data when new item is persisted`() {
        val item = item()
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCurrentlyPlaying.getCurrentlyPlaying(userId, accessToken) } returns item.right()
        every { currentlyPlayingRepository.existsByUserAndTrackAndObservedMinute(item) } returns false
        every { currentlyPlayingRepository.save(item) } just runs

        adapter.update(userId)

        verify { dashboardRefresh.notifyUserPlaybackData(userId) }
    }

    @Test
    fun `update does not persist duplicate item`() {
        val item = item()
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCurrentlyPlaying.getCurrentlyPlaying(userId, accessToken) } returns item.right()
        every { currentlyPlayingRepository.existsByUserAndTrackAndObservedMinute(item) } returns true

        adapter.update(userId)

        verify(exactly = 0) { currentlyPlayingRepository.save(any()) }
    }

    @Test
    fun `update does not notify playback data when item is duplicate`() {
        val item = item()
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCurrentlyPlaying.getCurrentlyPlaying(userId, accessToken) } returns item.right()
        every { currentlyPlayingRepository.existsByUserAndTrackAndObservedMinute(item) } returns true

        adapter.update(userId)

        verify(exactly = 0) { dashboardRefresh.notifyUserPlaybackData(any()) }
    }

    @Test
    fun `update does nothing when nothing is playing`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCurrentlyPlaying.getCurrentlyPlaying(userId, accessToken) } returns null.right()

        adapter.update(userId)

        verify(exactly = 0) { currentlyPlayingRepository.save(any()) }
        verify(exactly = 0) { dashboardRefresh.notifyUserPlaybackData(any()) }
    }

    @Test
    fun `update returns Left on domain error`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { spotifyCurrentlyPlaying.getCurrentlyPlaying(userId, accessToken) } returns PlaybackError.CURRENTLY_PLAYING_FETCH_FAILED.left()

        val result = adapter.update(userId)

        assertThat(result.isLeft()).isTrue()
        verify(exactly = 0) { currentlyPlayingRepository.save(any()) }
    }
}
