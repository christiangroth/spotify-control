package de.chrgroth.spotify.control.domain

import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.PlaybackError
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.AppPlaybackItem
import de.chrgroth.spotify.control.domain.model.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.User
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.CurrentlyPlayingRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.PlaybackStatePort
import de.chrgroth.spotify.control.domain.port.out.RecentlyPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyPlaybackPort
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RecentlyPlayedAdapterTests {

    private val userRepository: UserRepositoryPort = mockk()
    private val spotifyAccessToken: SpotifyAccessTokenPort = mockk()
    private val spotifyPlayback: SpotifyPlaybackPort = mockk(relaxed = true)
    private val currentlyPlayingRepository: CurrentlyPlayingRepositoryPort = mockk(relaxed = true)
    private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort = mockk()
    private val appPlaybackRepository: AppPlaybackRepositoryPort = mockk(relaxed = true)
    private val appArtistRepository: AppArtistRepositoryPort = mockk(relaxed = true)
    private val outboxPort: OutboxPort = mockk(relaxed = true)
    private val dashboardRefresh: DashboardRefreshPort = mockk(relaxed = true)
    private val playbackState: PlaybackStatePort = mockk(relaxed = true)
    private val appEnrichmentService: AppEnrichmentService = mockk(relaxed = true)

    private val adapter = PlaybackAdapter(
        userRepository,
        spotifyAccessToken,
        spotifyPlayback,
        currentlyPlayingRepository,
        recentlyPlayedRepository,
        appPlaybackRepository,
        appArtistRepository,
        outboxPort,
        dashboardRefresh,
        playbackState,
        appEnrichmentService,
        minimumProgressSeconds = 25L,
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

        adapter.enqueueFetchRecentlyPlayed()

        verify(exactly = 0) { outboxPort.enqueue(any()) }
    }

    @Test
    fun `enqueueUpdates enqueues one task per user`() {
        every { userRepository.findAll() } returns listOf(buildUser("user-1"), buildUser("user-2"))
        every { outboxPort.enqueue(any()) } just runs

        adapter.enqueueFetchRecentlyPlayed()

        verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.FetchRecentlyPlayed(UserId("user-1"))) }
        verify(exactly = 1) { outboxPort.enqueue(DomainOutboxEvent.FetchRecentlyPlayed(UserId("user-2"))) }
    }

    // --- update tests ---

    @Test
    fun `update persists new tracks`() {
        val items = listOf(item(1), item(2))
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns items.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { recentlyPlayedRepository.saveAll(any()) } just runs
        setupNoConsolidation()

        adapter.fetchRecentlyPlayed(userId)

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
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns items.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { recentlyPlayedRepository.saveAll(any()) } just runs
        setupNoConsolidation()

        adapter.fetchRecentlyPlayed(userId)

        verify { dashboardRefresh.notifyUserPlaybackData(userId) }
    }

    @Test
    fun `update does not notify playback data when no new items exist`() {
        val items = listOf(item(1))
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns items.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns setOf(items[0].playedAt)
        setupNoConsolidation()

        adapter.fetchRecentlyPlayed(userId)

        verify(exactly = 0) { dashboardRefresh.notifyUserPlaybackData(any()) }
    }

    @Test
    fun `update passes most recent playedAt as after cursor`() {
        val after = now - 2.hours
        val items = listOf(item(1))
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns after
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, after) } returns items.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { recentlyPlayedRepository.saveAll(any()) } just runs
        setupNoConsolidation()

        adapter.fetchRecentlyPlayed(userId)

        verify { spotifyPlayback.getRecentlyPlayed(userId, accessToken, after) }
    }

    @Test
    fun `update skips duplicate tracks`() {
        val items = listOf(item(1), item(2))
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns items.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns setOf(items[0].playedAt)
        every { recentlyPlayedRepository.saveAll(any()) } just runs
        setupNoConsolidation()

        adapter.fetchRecentlyPlayed(userId)

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
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns items.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns setOf(items[0].playedAt)
        setupNoConsolidation()

        adapter.fetchRecentlyPlayed(userId)

        verify(exactly = 0) { recentlyPlayedRepository.saveAll(any()) }
    }

    @Test
    fun `update does not call saveAll when no tracks returned`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        setupNoConsolidation()

        adapter.fetchRecentlyPlayed(userId)

        verify(exactly = 0) { recentlyPlayedRepository.saveAll(any()) }
    }

    @Test
    fun `update returns Left on domain error`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns PlaybackError.RECENTLY_PLAYED_FETCH_FAILED.left()

        val result = adapter.fetchRecentlyPlayed(userId)

        assertThat(result.isLeft()).isTrue()
        verify(exactly = 0) { recentlyPlayedRepository.saveAll(any()) }
    }

    // --- consolidation tests ---

    @Test
    fun `update deletes currently playing entries for completed tracks at end`() {
        val items = listOf(item(1))
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns items.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { recentlyPlayedRepository.saveAll(any()) } just runs
        setupNoConsolidation()

        adapter.fetchRecentlyPlayed(userId)

        verify { currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, setOf("track-1")) }
    }

    @Test
    fun `update converts partial plays exceeding 25s to app_playback`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()

        val olderFirst = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = now - 6.minutes)
        val olderSecond = currentlyPlayingItem("track-old", progressMs = 50_000L, observedAt = now - 4.minutes)
        val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderFirst, olderSecond, latestTrack)

        adapter.fetchRecentlyPlayed(userId)

        val savedSlot = slot<List<AppPlaybackItem>>()
        verify { appPlaybackRepository.saveAll(capture(savedSlot)) }
        assertThat(savedSlot.captured).hasSize(1)
        assertThat(savedSlot.captured[0].trackId).isEqualTo("track-old")
    }

    @Test
    fun `update computes playedSeconds using next track first observation timestamp`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()

        val firstObserved = now - 5.minutes
        val nextTrackObserved = now - 1.minutes
        val olderItem = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = firstObserved)
        val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = nextTrackObserved)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderItem, latestTrack)

        adapter.fetchRecentlyPlayed(userId)

        val savedSlot = slot<List<AppPlaybackItem>>()
        verify { appPlaybackRepository.saveAll(capture(savedSlot)) }
        val expectedPlayedSeconds = (nextTrackObserved - firstObserved).inWholeSeconds
        assertThat(savedSlot.captured[0].secondsPlayed).isEqualTo(expectedPlayedSeconds)
    }

    @Test
    fun `update computes playedSeconds using full session span to next different track observation`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()

        // track-old has multiple observations in one session; latestTrack is a different track observed after
        val firstObserved = now - 6.minutes
        val olderFirst = currentlyPlayingItem("track-old", progressMs = 35_000L, observedAt = firstObserved)
        val olderSecond = currentlyPlayingItem("track-old", progressMs = 45_000L, observedAt = now - 4.minutes)
        val olderThird = currentlyPlayingItem("track-old", progressMs = 50_000L, observedAt = now - 2.minutes)
        val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderFirst, olderSecond, olderThird, latestTrack)

        adapter.fetchRecentlyPlayed(userId)

        val savedSlot = slot<List<AppPlaybackItem>>()
        verify { appPlaybackRepository.saveAll(capture(savedSlot)) }
        // playedSeconds = (latestTrack.observedAt - firstObserved) / 1000 = 6 minutes
        val expectedPlayedSeconds = (now - firstObserved).inWholeSeconds
        assertThat(savedSlot.captured[0].secondsPlayed).isEqualTo(expectedPlayedSeconds)
    }

    @Test
    fun `update does not convert the sole non-completed track even when completed tracks exist`() {
        val completedItems = listOf(item(1))
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns completedItems.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { recentlyPlayedRepository.saveAll(any()) } just runs

        val olderItem = currentlyPlayingItem("track-old", progressMs = 45_000L, observedAt = now - 5.minutes)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderItem)

        adapter.fetchRecentlyPlayed(userId)

        verify(exactly = 0) { appPlaybackRepository.saveAll(any()) }
    }

    @Test
    fun `update uses next track observation as playedSeconds end time even when that track is completed`() {
        val completedItems = listOf(item(1))
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns completedItems.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { recentlyPlayedRepository.saveAll(any()) } just runs

        val firstObserved = now - 8.minutes
        val olderTrackSecond = currentlyPlayingItem("track-old", progressMs = 45_000L, observedAt = now - 6.minutes)
        val completedEntry = currentlyPlayingItem("track-1", progressMs = 5_000L, observedAt = now - 4.minutes)
        val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now - 2.minutes)
        val olderTrackFirst = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = firstObserved)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderTrackFirst, olderTrackSecond, completedEntry, latestTrack)

        adapter.fetchRecentlyPlayed(userId)

        val savedSlot = slot<List<AppPlaybackItem>>()
        verify { appPlaybackRepository.saveAll(capture(savedSlot)) }
        val expectedPlayedSeconds = (now - 4.minutes - firstObserved).inWholeSeconds
        assertThat(savedSlot.captured[0].secondsPlayed).isEqualTo(expectedPlayedSeconds)
    }

    @Test
    fun `update creates two partial played items when same track played twice`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()

        // Session 1 of track-a: played from t-10m to t-9m, then skipped
        val session1First = currentlyPlayingItem("track-a", progressMs = 5_000L, observedAt = now - 10.minutes)
        val session1Second = currentlyPlayingItem("track-a", progressMs = 30_000L, observedAt = now - 9.minutes)
        // Different track played in between
        val differentTrack = currentlyPlayingItem("track-b", progressMs = 30_000L, observedAt = now - 7.minutes)
        // Session 2 of track-a: played from beginning again at t-6m
        val session2First = currentlyPlayingItem("track-a", progressMs = 5_000L, observedAt = now - 6.minutes)
        val session2Second = currentlyPlayingItem("track-a", progressMs = 30_000L, observedAt = now - 5.minutes)
        // track-latest is the protected session
        val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(
            session1First, session1Second, differentTrack, session2First, session2Second, latestTrack,
        )

        adapter.fetchRecentlyPlayed(userId)

        val savedSlot = slot<List<AppPlaybackItem>>()
        verify { appPlaybackRepository.saveAll(capture(savedSlot)) }
        // Both sessions of track-a should be saved (plus track-b session is the intermediate, track-latest is protected)
        val trackAItems = savedSlot.captured.filter { it.trackId == "track-a" }
        assertThat(trackAItems).hasSize(2)
        assertThat(trackAItems.map { it.playedAt }).containsExactlyInAnyOrder(
            now - 10.minutes,
            now - 6.minutes,
        )
    }

    @Test
    fun `update does not convert the most recently observed non-completed session`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()

        val latestTrack = currentlyPlayingItem("track-latest", progressMs = 60_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(latestTrack)

        adapter.fetchRecentlyPlayed(userId)

        verify(exactly = 0) { appPlaybackRepository.saveAll(any()) }
    }

    @Test
    fun `update does not convert partial plays shorter than minimum progress`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()

        val olderTrack = currentlyPlayingItem("track-old", progressMs = 10_000L, observedAt = now - 5.minutes)
        val latestTrack = currentlyPlayingItem("track-latest", progressMs = 5_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderTrack, latestTrack)

        adapter.fetchRecentlyPlayed(userId)

        verify(exactly = 0) { appPlaybackRepository.saveAll(any()) }
    }

    @Test
    fun `update notifies dashboard when partial plays are converted`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()

        val olderTrack = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = now - 5.minutes)
        val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderTrack, latestTrack)

        adapter.fetchRecentlyPlayed(userId)

        verify { dashboardRefresh.notifyUserPlaybackData(userId) }
    }

    @Test
    fun `update deletes converted currently playing entries at end`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()

        val olderTrack = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = now - 5.minutes)
        val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderTrack, latestTrack)

        adapter.fetchRecentlyPlayed(userId)

        verify { currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, setOf("track-old")) }
    }

    @Test
    fun `update deletes both completed and converted tracks together at end`() {
        val completedItems = listOf(item(1))
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns completedItems.right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()
        every { recentlyPlayedRepository.saveAll(any()) } just runs

        val olderTrack = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = now - 5.minutes)
        val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderTrack, latestTrack)

        adapter.fetchRecentlyPlayed(userId)

        verify { currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, setOf("track-1", "track-old")) }
    }

    @Test
    fun `update does not delete protected track entries when it has a second session`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()

        // Session 1 of track-a is eligible; session 2 of track-a is the latest non-completed (protected)
        val session1 = currentlyPlayingItem("track-a", progressMs = 30_000L, observedAt = now - 6.minutes)
        val different = currentlyPlayingItem("track-b", progressMs = 10_000L, observedAt = now - 4.minutes)
        val session2 = currentlyPlayingItem("track-a", progressMs = 5_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(session1, different, session2)

        adapter.fetchRecentlyPlayed(userId)

        // track-a must NOT be deleted (session 2 is protected)
        // track-b also has no completed tracks to delete and is not converted (it's a session of track-b with progress < 25s... wait, 10s)
        verify { currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, emptySet()) }
    }

    @Test
    fun `update saves partial played item with first observedAt as playedAt`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()

        val firstObserved = now - 5.minutes
        val olderTrack = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = firstObserved)
        val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderTrack, latestTrack)

        adapter.fetchRecentlyPlayed(userId)

        val savedSlot = slot<List<AppPlaybackItem>>()
        verify { appPlaybackRepository.saveAll(capture(savedSlot)) }
        assertThat(savedSlot.captured[0].playedAt).isEqualTo(firstObserved)
    }

    @Test
    fun `update does not save partial played item to recently played repository`() {
        every { spotifyAccessToken.getValidAccessToken(userId) } returns accessToken
        every { recentlyPlayedRepository.findMostRecentPlayedAt(userId) } returns null
        every { spotifyPlayback.getRecentlyPlayed(userId, accessToken, null) } returns emptyList<RecentlyPlayedItem>().right()
        every { recentlyPlayedRepository.findExistingPlayedAts(userId, any()) } returns emptySet()

        val olderTrack = currentlyPlayingItem("track-old", progressMs = 30_000L, observedAt = now - 5.minutes)
        val latestTrack = currentlyPlayingItem("track-latest", progressMs = 10_000L, observedAt = now)
        every { currentlyPlayingRepository.findByUserId(userId) } returns listOf(olderTrack, latestTrack)

        adapter.fetchRecentlyPlayed(userId)

        verify(exactly = 0) { recentlyPlayedRepository.saveAll(any()) }
    }
}

