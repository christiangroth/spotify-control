package de.chrgroth.spotify.control.domain

import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.PlaybackError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.CurrentlyPlayingRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.RecentlyPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyRecentlyPlayedPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RecentlyPlayedAdapterTests {

    private val userRepository: UserRepositoryPort = mockk()
    private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
    private val spotifyRecentlyPlayed: SpotifyRecentlyPlayedPort = mockk()
    private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort = mockk()
    private val currentlyPlayingRepository: CurrentlyPlayingRepositoryPort = mockk(relaxed = true)
    private val outboxPort: OutboxPort = mockk()
    private val dashboardRefresh: DashboardRefreshPort = mockk(relaxed = true)

    private val adapter = RecentlyPlayedAdapter(
        userRepository,
        spotifyAccessToken,
        spotifyRecentlyPlayed,
        recentlyPlayedRepository,
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

    private fun item(index: Int, forUserId: UserId = userId) = RecentlyPlayedItem(
        spotifyUserId = forUserId,
        trackId = "track-$index",
        trackName = "Track $index",
        artistIds = listOf("artist-id-$index"),
        artistNames = listOf("Artist $index"),
        playedAt = now - index.hours,
    )

    private fun currentlyPlayingItem(
        trackId: String,
        progressMs: Long,
        observedAt: Instant = now,
    ) = CurrentlyPlayingItem(
        spotifyUserId = userId,
        trackId = trackId,
        trackName = "Track $trackId",
        artistIds = listOf("artist-$trackId"),
        artistNames = listOf("Artist $trackId"),
        progressMs = progressMs,
        durationMs = 200_000L,
        isPlaying = true,
        observedAt = observedAt,
    )

    private fun setupNoConsolidation() {
        every { currentlyPlayingRepository.findByUserId(userId) } returns emptyList()
    }

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

        verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.FetchRecentlyPlayed(UserId("user-1"))) }
        verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.FetchRecentlyPlayed(UserId("user-2"))) }
    }

    // --- update tests ---

    @Test
    fun `update persists new tracks`() {
        val items = listOf(item(1), item(2))
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns items.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { recentlyPlayedRepository.saveAll(any()) } just runs
        setupNoConsolidation()

        adapter.update(userId)

        val savedSlot = slot<List<RecentlyPlayedItem>>()
        verify { recentlyPlayedRepository.saveAll(capture(savedSlot)) }
        assertThat(savedSlot.captured).hasSize(2)
        assertThat(savedSlot.captured.map { it.trackId }).containsExactlyInAnyOrder("track-1", "track-2")
    }

    @Test
    fun `update notifies playback data when new items are persisted`() {
        val items = listOf(item(1))
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns items.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { recentlyPlayedRepository.saveAll(any()) } just runs
        setupNoConsolidation()

        adapter.update(userId)

        verify { dashboardRefresh.notifyUserPlaybackData(userId) }
    }

    @Test
    fun `update does not notify playback data when no new items exist`() {
        val items = listOf(item(1))
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns items.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns setOf(items[0].playedAt)
        setupNoConsolidation()

        adapter.update(userId)

        verify(exactly = 0) { dashboardRefresh.notifyUserPlaybackData(any()) }
    }

    @Test
    fun `update passes most recent playedAt as after cursor`() {
        val after = now - 2.hours
        val items = listOf(item(1))
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns after
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, after) } returns items.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { recentlyPlayedRepository.saveAll(any()) } just runs
        setupNoConsolidation()

        adapter.update(userId)

        verify { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, after) }
    }

    @Test
    fun `update skips duplicate tracks`() {
        val items = listOf(item(1), item(2))
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns items.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns setOf(items[0].playedAt)
        every { recentlyPlayedRepository.saveAll(any()) } just runs
        setupNoConsolidation()

        adapter.update(userId)

        val savedSlot = slot<List<RecentlyPlayedItem>>()
        verify { recentlyPlayedRepository.saveAll(capture(savedSlot)) }
        assertThat(savedSlot.captured).hasSize(1)
        assertThat(savedSlot.captured[0].trackId).isEqualTo("track-2")
    }

    @Test
    fun `update does not call saveAll when all tracks are duplicates`() {
        val items = listOf(item(1))
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns items.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns setOf(items[0].playedAt)
        setupNoConsolidation()

        adapter.update(userId)

        verify(exactly = 0) { recentlyPlayedRepository.saveAll(any()) }
    }

    @Test
    fun `update does not call saveAll when no tracks returned`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        setupNoConsolidation()

        adapter.update(userId)

        verify(exactly = 0) { recentlyPlayedRepository.saveAll(any()) }
    }

    @Test
    fun `update returns Left on domain error`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns PlaybackError.RECENTLY_PLAYED_FETCH_FAILED.left()

        val result = adapter.update(userId)

        assertThat(result.isLeft()).isTrue()
        verify(exactly = 0) { recentlyPlayedRepository.saveAll(any()) }
    }

    // --- consolidation tests ---

    @Test
    fun `update deletes currently playing entries for completed tracks`() {
        val items = listOf(item(1))
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns items.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { recentlyPlayedRepository.saveAll(any()) } just runs
        setupNoConsolidation()

        adapter.update(userId)

        verify { currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, setOf("track-1")) }
    }

    @Test
    fun `update converts partial plays exceeding 25s to recently played`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { recentlyPlayedRepository.saveAll(any()) } just runs

        val older = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = now - 5.minutes)
        val latest = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(older, latest)

        adapter.update(userId)

        val savedSlot = slot<List<RecentlyPlayedItem>>()
        verify { recentlyPlayedRepository.saveAll(capture(savedSlot)) }
        assertThat(savedSlot.captured).hasSize(1)
        assertThat(savedSlot.captured[0].trackId).isEqualTo("track-old")
    }

    @Test
    fun `update does not convert the most recently observed track`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()

        val latest = currentlyPlayingItem("track-latest", progressMs = 60_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(latest)

        adapter.update(userId)

        verify(exactly = 0) { recentlyPlayedRepository.saveAll(any()) }
    }

    @Test
    fun `update does not convert partial plays shorter than 25s`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()

        val older = currentlyPlayingItem("track-old", progressMs = 10_000L, observedAt = now - 5.minutes)
        val latest = currentlyPlayingItem("track-latest", progressMs = 5_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(older, latest)

        adapter.update(userId)

        verify(exactly = 0) { recentlyPlayedRepository.saveAll(any()) }
    }

    @Test
    fun `update notifies dashboard when partial plays are converted`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { recentlyPlayedRepository.saveAll(any()) } just runs

        val older = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = now - 5.minutes)
        val latest = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(older, latest)

        adapter.update(userId)

        verify { dashboardRefresh.notifyUserPlaybackData(userId) }
    }

    @Test
    fun `update deletes converted currently playing entries`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { recentlyPlayedRepository.saveAll(any()) } just runs

        val older = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = now - 5.minutes)
        val latest = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(older, latest)

        adapter.update(userId)

        verify { currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, setOf("track-old")) }
    }
}
