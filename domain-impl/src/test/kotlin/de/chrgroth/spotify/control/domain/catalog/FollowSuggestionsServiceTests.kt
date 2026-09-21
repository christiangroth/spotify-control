package de.chrgroth.spotify.control.domain.catalog

import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.FollowSuggestionsView
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationRankEntry
import de.chrgroth.spotify.control.domain.model.playback.aggregation.PlaybackAggregation
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackAggregationRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.readmodel.FollowSuggestionsViewRepositoryPort
import de.chrgroth.spotify.control.domain.user.CurrentUserResolver
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FollowSuggestionsServiceTests {

  private val appArtistRepository: AppArtistRepositoryPort = mockk()
  private val aggregationRepository: PlaybackAggregationRepositoryPort = mockk()
  private val followSuggestionsViewRepository: FollowSuggestionsViewRepositoryPort = mockk()
  private val currentUserResolver: CurrentUserResolver = mockk()

  private val service = FollowSuggestionsService(
    appArtistRepository = appArtistRepository,
    aggregationRepository = aggregationRepository,
    followSuggestionsViewRepository = followSuggestionsViewRepository,
    currentUserResolver = currentUserResolver,
    unfollowLookbackDays = 90,
    unfollowMinAgeDays = 30,
    unfollowMaxPlaybackSeconds = 0,
    followLookbackWeeks = 4,
    followTopN = 2,
    followMinOccurrences = 2,
  )

  private val userId = UserId("user-1")

  private val syncTimestamp = Instant.fromEpochSeconds(0)
  private val someWeekStart = LocalDate(2024, 1, 1)
  private val anotherWeekStart = LocalDate(2024, 1, 8)

  // --- findUnfollowCandidates ---

  @Test
  fun `findUnfollowCandidates returns empty list when no artists are followed`() {
    every { appArtistRepository.findFollowed() } returns emptyList()

    val result = service.findUnfollowCandidates()

    assertThat(result).isEmpty()
  }

  @Test
  fun `findUnfollowCandidates excludes recently followed artists regardless of playback`() {
    val recentlyFollowed = artist("artist-1", followedSince = Clock.System.now().minus(10.days))
    every { appArtistRepository.findFollowed() } returns listOf(recentlyFollowed)

    val result = service.findUnfollowCandidates()

    assertThat(result).isEmpty()
  }

  @Test
  fun `findUnfollowCandidates includes eligible artist with no plays in lookback window`() {
    val eligible = artist("artist-1", followedSince = Clock.System.now().minus(40.days))
    every { appArtistRepository.findFollowed() } returns listOf(eligible)
    every { aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, any(), any()) } returns emptyList()

    val result = service.findUnfollowCandidates()

    assertThat(result).hasSize(1)
    assertThat(result.first().artistId).isEqualTo(ArtistId("artist-1"))
    assertThat(result.first().lookbackPlaybackSeconds).isZero()
  }

  @Test
  fun `findUnfollowCandidates excludes eligible artist with plays above threshold`() {
    val eligible = artist("artist-1", followedSince = Clock.System.now().minus(40.days))
    every { appArtistRepository.findFollowed() } returns listOf(eligible)
    every { aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, any(), any()) } returns listOf(
      weekAggregation(someWeekStart, rankEntry("artist-1", seconds = 120)),
    )

    val result = service.findUnfollowCandidates()

    assertThat(result).isEmpty()
  }

  @Test
  fun `findUnfollowCandidates sorts by ascending lookback playback seconds`() {
    val serviceWithHigherThreshold = FollowSuggestionsService(
      appArtistRepository = appArtistRepository,
      aggregationRepository = aggregationRepository,
      followSuggestionsViewRepository = followSuggestionsViewRepository,
      currentUserResolver = currentUserResolver,
      unfollowLookbackDays = 90,
      unfollowMinAgeDays = 30,
      unfollowMaxPlaybackSeconds = 100,
      followLookbackWeeks = 4,
      followTopN = 2,
      followMinOccurrences = 2,
    )
    val artistLowPlays = artist("artist-low", followedSince = Clock.System.now().minus(40.days))
    val artistNoPlays = artist("artist-none", followedSince = Clock.System.now().minus(40.days))
    every { appArtistRepository.findFollowed() } returns listOf(artistLowPlays, artistNoPlays)
    every { aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, any(), any()) } returns listOf(
      weekAggregation(someWeekStart, rankEntry("artist-low", seconds = 50)),
    )

    val result = serviceWithHigherThreshold.findUnfollowCandidates()

    assertThat(result.map { it.artistId }).containsExactly(ArtistId("artist-none"), ArtistId("artist-low"))
  }

  // --- findFollowCandidates ---

  @Test
  fun `findFollowCandidates returns empty list when no weekly aggregations exist`() {
    every { appArtistRepository.findFollowed() } returns emptyList()
    every { aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, any(), any()) } returns emptyList()

    val result = service.findFollowCandidates()

    assertThat(result).isEmpty()
  }

  @Test
  fun `findFollowCandidates includes not-yet-followed artist reaching minimum top-N occurrences`() {
    every { appArtistRepository.findFollowed() } returns emptyList()
    every { aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, any(), any()) } returns listOf(
      weekAggregation(someWeekStart, rankEntry("artist-1", seconds = 300)),
      weekAggregation(anotherWeekStart, rankEntry("artist-1", seconds = 200)),
    )

    val result = service.findFollowCandidates()

    assertThat(result).hasSize(1)
    val candidate = result.first()
    assertThat(candidate.artistId).isEqualTo(ArtistId("artist-1"))
    assertThat(candidate.topRankWeeks).isEqualTo(2)
    assertThat(candidate.recentPlaybackSeconds).isEqualTo(500)
  }

  @Test
  fun `findFollowCandidates excludes artist below minimum top-N occurrences`() {
    every { appArtistRepository.findFollowed() } returns emptyList()
    every { aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, any(), any()) } returns listOf(
      weekAggregation(someWeekStart, rankEntry("artist-1", seconds = 300)),
    )

    val result = service.findFollowCandidates()

    assertThat(result).isEmpty()
  }

  @Test
  fun `findFollowCandidates excludes already followed artist`() {
    every { appArtistRepository.findFollowed() } returns listOf(artist("artist-1", followedSince = syncTimestamp))
    every { aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, any(), any()) } returns listOf(
      weekAggregation(someWeekStart, rankEntry("artist-1", seconds = 300)),
      weekAggregation(anotherWeekStart, rankEntry("artist-1", seconds = 200)),
    )

    val result = service.findFollowCandidates()

    assertThat(result).isEmpty()
  }

  @Test
  fun `findFollowCandidates ignores artist ranked below top-N in a week`() {
    every { appArtistRepository.findFollowed() } returns emptyList()
    every { aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, any(), any()) } returns listOf(
      weekAggregation(someWeekStart, rankEntry("artist-1", seconds = 300), rankEntry("artist-2", seconds = 200), rankEntry("artist-3", seconds = 100)),
      weekAggregation(anotherWeekStart, rankEntry("artist-3", seconds = 400), rankEntry("artist-2", seconds = 300), rankEntry("artist-1", seconds = 1)),
    )

    val result = service.findFollowCandidates()

    // artist-1 only ranks top-2 (followTopN=2) in the first week, artist-3 only in the second week -> neither reaches followMinOccurrences=2
    assertThat(result.map { it.artistId }).containsExactly(ArtistId("artist-2"))
  }

  // --- view repository / outbox handler ---

  @Test
  fun `getFollowSuggestionsView returns empty view when nothing has been precomputed yet`() {
    every { followSuggestionsViewRepository.find() } returns null

    val result = service.getFollowSuggestionsView()

    assertThat(result).isEqualTo(FollowSuggestionsView())
  }

  @Test
  fun `getFollowSuggestionsView returns the precomputed view`() {
    val precomputed = FollowSuggestionsView(unfollowCandidates = emptyList(), followCandidates = emptyList())
    every { followSuggestionsViewRepository.find() } returns precomputed

    val result = service.getFollowSuggestionsView()

    assertThat(result).isEqualTo(precomputed)
  }

  @Test
  fun `rebuildFollowSuggestionsView saves freshly computed candidates`() {
    every { appArtistRepository.findFollowed() } returns emptyList()
    every { aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, any(), any()) } returns emptyList()
    every { followSuggestionsViewRepository.save(any()) } just runs

    service.rebuildFollowSuggestionsView()

    verify { followSuggestionsViewRepository.save(FollowSuggestionsView(followCandidates = emptyList(), unfollowCandidates = emptyList())) }
  }

  @Test
  fun `handle RebuildFollowSuggestions skips when no user exists`() {
    every { currentUserResolver.userId() } returns null

    val result = service.handle(DomainOutboxEvent.RebuildFollowSuggestions())

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { followSuggestionsViewRepository.save(any()) }
  }

  @Test
  fun `handle RebuildFollowSuggestions rebuilds the view for the current user`() {
    every { currentUserResolver.userId() } returns userId
    every { appArtistRepository.findFollowed() } returns emptyList()
    every { aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, any(), any()) } returns emptyList()
    every { followSuggestionsViewRepository.save(any()) } just runs

    val result = service.handle(DomainOutboxEvent.RebuildFollowSuggestions())

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { followSuggestionsViewRepository.save(any()) }
  }

  private fun artist(id: String, followedSince: Instant?) = AppArtist(
    id = ArtistId(id),
    artistName = "Artist $id",
    lastSync = syncTimestamp,
    followed = true,
    followedSince = followedSince,
  )

  private fun rankEntry(artistId: String, seconds: Long) = AggregationRankEntry(id = artistId, name = "Artist $artistId", totalSeconds = seconds)

  private fun weekAggregation(periodStart: LocalDate, vararg artistEntries: AggregationRankEntry) = PlaybackAggregation(
    type = AggregationPeriodType.WEEK,
    periodStart = periodStart,
    totalPlaybackSeconds = artistEntries.sumOf { it.totalSeconds },
    eventCount = artistEntries.size.toLong(),
    distinctArtistCount = artistEntries.size,
    distinctTrackCount = 0,
    distinctAlbumCount = 0,
    artistEntries = artistEntries.toList(),
    albumEntries = emptyList(),
    trackEntries = emptyList(),
    activityEntries = emptyList(),
  )
}
