package de.chrgroth.spotify.control.domain

import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.PlaybackError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItemComputed
import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.ComputedRecentlyPlayedRepositoryPort
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
    private val computedRecentlyPlayedRepository: ComputedRecentlyPlayedRepositoryPort = mockk(relaxed = true)
    private val outboxPort: OutboxPort = mockk()
    private val dashboardRefresh: DashboardRefreshPort = mockk(relaxed = true)

    private val adapter = RecentlyPlayedAdapter(
        userRepository,
        spotifyAccessToken,
        spotifyRecentlyPlayed,
        recentlyPlayedRepository,
        currentlyPlayingRepository,
        computedRecentlyPlayedRepository,
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
    fun `update deletes currently playing entries for completed tracks at end`() {
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
    fun `update converts partial plays exceeding 25s to computed recently played`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { computedRecentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { computedRecentlyPlayedRepository.saveAll(any()) } just runs

        val olderFirst = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = now - 6.minutes)
        val olderSecond = currentlyPlayingItem("track-old", progressMs = 50_000L, observedAt = now - 4.minutes)
        val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderFirst, olderSecond, latestTrack)

        adapter.update(userId)

        val savedSlot = slot<List<RecentlyPlayedItemComputed>>()
        verify { computedRecentlyPlayedRepository.saveAll(capture(savedSlot)) }
        assertThat(savedSlot.captured).hasSize(1)
        assertThat(savedSlot.captured[0].trackId).isEqualTo("track-old")
    }

    @Test
    fun `update computes playedMs using next track first observation timestamp`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { computedRecentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { computedRecentlyPlayedRepository.saveAll(any()) } just runs

        val firstObserved = now - 5.minutes
        val nextTrackObserved = now - 1.minutes
        val olderItem = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = firstObserved)
        val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = nextTrackObserved)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderItem, latestTrack)

        adapter.update(userId)

        val savedSlot = slot<List<RecentlyPlayedItemComputed>>()
        verify { computedRecentlyPlayedRepository.saveAll(capture(savedSlot)) }
        val expectedPlayedMs = (nextTrackObserved - firstObserved).inWholeMilliseconds
        assertThat(savedSlot.captured[0].playedMs).isEqualTo(expectedPlayedMs)
    }

    @Test
    fun `update does not convert the sole non-completed track even when completed tracks exist`() {
        val completedItems = listOf(item(1)) // track-1 in recently played
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns completedItems.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { recentlyPlayedRepository.saveAll(any()) } just runs

        // track-old is the only non-completed item → latestNonCompletedTrackId = track-old → protected
        val olderItem = currentlyPlayingItem("track-old", progressMs = 45_000L, observedAt = now - 5.minutes)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderItem)

        adapter.update(userId)

        verify(exactly = 0) { computedRecentlyPlayedRepository.saveAll(any()) }
    }

    @Test
    fun `update uses next track observation as playedMs end time even when that track is completed`() {
        val completedItems = listOf(item(1)) // track-1 completed via recently played
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns completedItems.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { recentlyPlayedRepository.saveAll(any()) } just runs
        every { computedRecentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { computedRecentlyPlayedRepository.saveAll(any()) } just runs

        // track-old: eligible (not latest, not completed, maxProgress > 25s)
        // track-1: completed, observed in currentlyPlaying after track-old → provides timing reference
        // track-latest: most recent non-completed → latestNonCompletedTrackId (protected)
        val firstObserved = now - 8.minutes
        val olderTrackSecond = currentlyPlayingItem("track-old", progressMs = 45_000L, observedAt = now - 6.minutes)
        val completedEntry = currentlyPlayingItem("track-1", progressMs = 5_000L, observedAt = now - 4.minutes)
        val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now - 2.minutes)
        val olderTrackFirst = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = firstObserved)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderTrackFirst, olderTrackSecond, completedEntry, latestTrack)

        adapter.update(userId)

        val savedSlot = slot<List<RecentlyPlayedItemComputed>>()
        verify { computedRecentlyPlayedRepository.saveAll(capture(savedSlot)) }
        // playedMs = first observation of next item (track-1 at now-4m) - firstObservedAt (now-8m)
        val expectedPlayedMs = (now - 4.minutes - firstObserved).inWholeMilliseconds
        assertThat(savedSlot.captured[0].playedMs).isEqualTo(expectedPlayedMs)
    }

    @Test
    fun `update does not convert the most recently observed non-completed track`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()

        val latestTrack = currentlyPlayingItem("track-latest", progressMs = 60_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(latestTrack)

        adapter.update(userId)

        verify(exactly = 0) { computedRecentlyPlayedRepository.saveAll(any()) }
    }

    @Test
    fun `update does not convert partial plays shorter than 25s`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()

        val olderTrack = currentlyPlayingItem("track-old", progressMs = 10_000L, observedAt = now - 5.minutes)
        val latestTrack = currentlyPlayingItem("track-latest", progressMs = 5_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderTrack, latestTrack)

        adapter.update(userId)

        verify(exactly = 0) { computedRecentlyPlayedRepository.saveAll(any()) }
    }

    @Test
    fun `update notifies dashboard when partial plays are converted`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { computedRecentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { computedRecentlyPlayedRepository.saveAll(any()) } just runs

        val olderTrack = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = now - 5.minutes)
        val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderTrack, latestTrack)

        adapter.update(userId)

        verify { dashboardRefresh.notifyUserPlaybackData(userId) }
    }

    @Test
    fun `update deletes converted currently playing entries at end`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { computedRecentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { computedRecentlyPlayedRepository.saveAll(any()) } just runs

        val olderTrack = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = now - 5.minutes)
        val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderTrack, latestTrack)

        adapter.update(userId)

        // Deletes converted track IDs (no completed track IDs from recently-played in this test)
        verify { currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, setOf("track-old")) }
    }

    @Test
    fun `update deletes both completed and converted tracks together at end`() {
        val completedItems = listOf(item(1))
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns completedItems.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { recentlyPlayedRepository.saveAll(any()) } just runs
        every { computedRecentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { computedRecentlyPlayedRepository.saveAll(any()) } just runs

        val olderTrack = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = now - 5.minutes)
        val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderTrack, latestTrack)

        adapter.update(userId)

        verify { currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, setOf("track-1", "track-old")) }
    }

    @Test
    fun `update saves computed item with first observedAt as playedAt`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { computedRecentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { computedRecentlyPlayedRepository.saveAll(any()) } just runs

        val firstObserved = now - 5.minutes
        val olderTrack = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = firstObserved)
        val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderTrack, latestTrack)

        adapter.update(userId)

        val savedSlot = slot<List<RecentlyPlayedItemComputed>>()
        verify { computedRecentlyPlayedRepository.saveAll(capture(savedSlot)) }
        assertThat(savedSlot.captured[0].playedAt).isEqualTo(firstObserved)
    }

    @Test
    fun `update does not save computed item to recently played repository`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { computedRecentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { computedRecentlyPlayedRepository.saveAll(any()) } just runs

        val olderTrack = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = now - 5.minutes)
        val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderTrack, latestTrack)

        adapter.update(userId)

        verify(exactly = 0) { recentlyPlayedRepository.saveAll(any()) }
    }
}
