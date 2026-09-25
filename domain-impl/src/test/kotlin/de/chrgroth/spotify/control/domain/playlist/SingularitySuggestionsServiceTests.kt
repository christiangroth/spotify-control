package de.chrgroth.spotify.control.domain.playlist

import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationRankEntry
import de.chrgroth.spotify.control.domain.model.playback.aggregation.PlaybackAggregation
import de.chrgroth.spotify.control.domain.model.playlist.SingularitySuggestionsView
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackAggregationRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.readmodel.SingularitySuggestionsViewRepositoryPort
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

class SingularitySuggestionsServiceTests {

  private val appArtistRepository: AppArtistRepositoryPort = mockk()
  private val aggregationRepository: PlaybackAggregationRepositoryPort = mockk()
  private val singularitySuggestionsViewRepository: SingularitySuggestionsViewRepositoryPort = mockk()
  private val currentUserResolver: CurrentUserResolver = mockk()

  private val service = SingularitySuggestionsService(
    appArtistRepository = appArtistRepository,
    aggregationRepository = aggregationRepository,
    singularitySuggestionsViewRepository = singularitySuggestionsViewRepository,
    currentUserResolver = currentUserResolver,
    excludeLookbackDays = 90,
    excludeMinAgeDays = 30,
    excludeMaxPlaybackSeconds = 0,
    includeLookbackWeeks = 4,
    includeTopN = 2,
    includeMinOccurrences = 2,
  )

  private val userId = UserId("user-1")

  private val syncTimestamp = Instant.fromEpochSeconds(0)
  private val someWeekStart = LocalDate(2024, 1, 1)
  private val anotherWeekStart = LocalDate(2024, 1, 8)

  // --- findExcludeCandidates ---

  @Test
  fun `findExcludeCandidates returns empty list when no artists are included`() {
    every { appArtistRepository.findAll() } returns emptyList()

    val result = service.findExcludeCandidates()

    assertThat(result).isEmpty()
  }

  @Test
  fun `findExcludeCandidates excludes recently added current track regardless of playback`() {
    val recentlyAdded = artist("artist-1", included = true, currentTrackAddedAt = Clock.System.now().minus(10.days))
    every { appArtistRepository.findAll() } returns listOf(recentlyAdded)

    val result = service.findExcludeCandidates()

    assertThat(result).isEmpty()
  }

  @Test
  fun `findExcludeCandidates excludes not-included artist regardless of age or playback`() {
    val notIncluded = artist("artist-1", included = false, currentTrackAddedAt = Clock.System.now().minus(40.days))
    every { appArtistRepository.findAll() } returns listOf(notIncluded)

    val result = service.findExcludeCandidates()

    assertThat(result).isEmpty()
  }

  @Test
  fun `findExcludeCandidates includes eligible artist with no plays in lookback window`() {
    val eligible = artist("artist-1", included = true, currentTrackAddedAt = Clock.System.now().minus(40.days))
    every { appArtistRepository.findAll() } returns listOf(eligible)
    every { aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, any(), any()) } returns emptyList()

    val result = service.findExcludeCandidates()

    assertThat(result).hasSize(1)
    assertThat(result.first().artistId).isEqualTo(ArtistId("artist-1"))
    assertThat(result.first().lookbackPlaybackSeconds).isZero()
  }

  @Test
  fun `findExcludeCandidates excludes eligible artist with plays above threshold`() {
    val eligible = artist("artist-1", included = true, currentTrackAddedAt = Clock.System.now().minus(40.days))
    every { appArtistRepository.findAll() } returns listOf(eligible)
    every { aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, any(), any()) } returns listOf(
      weekAggregation(someWeekStart, rankEntry("artist-1", seconds = 120)),
    )

    val result = service.findExcludeCandidates()

    assertThat(result).isEmpty()
  }

  @Test
  fun `findExcludeCandidates sorts by ascending lookback playback seconds`() {
    val serviceWithHigherThreshold = SingularitySuggestionsService(
      appArtistRepository = appArtistRepository,
      aggregationRepository = aggregationRepository,
      singularitySuggestionsViewRepository = singularitySuggestionsViewRepository,
      currentUserResolver = currentUserResolver,
      excludeLookbackDays = 90,
      excludeMinAgeDays = 30,
      excludeMaxPlaybackSeconds = 100,
      includeLookbackWeeks = 4,
      includeTopN = 2,
      includeMinOccurrences = 2,
    )
    val artistLowPlays = artist("artist-low", included = true, currentTrackAddedAt = Clock.System.now().minus(40.days))
    val artistNoPlays = artist("artist-none", included = true, currentTrackAddedAt = Clock.System.now().minus(40.days))
    every { appArtistRepository.findAll() } returns listOf(artistLowPlays, artistNoPlays)
    every { aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, any(), any()) } returns listOf(
      weekAggregation(someWeekStart, rankEntry("artist-low", seconds = 50)),
    )

    val result = serviceWithHigherThreshold.findExcludeCandidates()

    assertThat(result.map { it.artistId }).containsExactly(ArtistId("artist-none"), ArtistId("artist-low"))
  }

  // --- findIncludeCandidates ---

  @Test
  fun `findIncludeCandidates returns empty list when no weekly aggregations exist`() {
    every { appArtistRepository.findAll() } returns emptyList()
    every { aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, any(), any()) } returns emptyList()

    val result = service.findIncludeCandidates()

    assertThat(result).isEmpty()
  }

  @Test
  fun `findIncludeCandidates includes not-yet-included artist reaching minimum top-N occurrences`() {
    every { appArtistRepository.findAll() } returns emptyList()
    every { aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, any(), any()) } returns listOf(
      weekAggregation(someWeekStart, rankEntry("artist-1", seconds = 300)),
      weekAggregation(anotherWeekStart, rankEntry("artist-1", seconds = 200)),
    )

    val result = service.findIncludeCandidates()

    assertThat(result).hasSize(1)
    val candidate = result.first()
    assertThat(candidate.artistId).isEqualTo(ArtistId("artist-1"))
    assertThat(candidate.topRankWeeks).isEqualTo(2)
    assertThat(candidate.recentPlaybackSeconds).isEqualTo(500)
  }

  @Test
  fun `findIncludeCandidates excludes artist below minimum top-N occurrences`() {
    every { appArtistRepository.findAll() } returns emptyList()
    every { aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, any(), any()) } returns listOf(
      weekAggregation(someWeekStart, rankEntry("artist-1", seconds = 300)),
    )

    val result = service.findIncludeCandidates()

    assertThat(result).isEmpty()
  }

  @Test
  fun `findIncludeCandidates excludes already included artist`() {
    every { appArtistRepository.findAll() } returns listOf(artist("artist-1", included = true, currentTrackAddedAt = syncTimestamp))
    every { aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, any(), any()) } returns listOf(
      weekAggregation(someWeekStart, rankEntry("artist-1", seconds = 300)),
      weekAggregation(anotherWeekStart, rankEntry("artist-1", seconds = 200)),
    )

    val result = service.findIncludeCandidates()

    assertThat(result).isEmpty()
  }

  @Test
  fun `findIncludeCandidates ignores artist ranked below top-N in a week`() {
    every { appArtistRepository.findAll() } returns emptyList()
    every { aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, any(), any()) } returns listOf(
      weekAggregation(someWeekStart, rankEntry("artist-1", seconds = 300), rankEntry("artist-2", seconds = 200), rankEntry("artist-3", seconds = 100)),
      weekAggregation(anotherWeekStart, rankEntry("artist-3", seconds = 400), rankEntry("artist-2", seconds = 300), rankEntry("artist-1", seconds = 1)),
    )

    val result = service.findIncludeCandidates()

    // artist-1 only ranks top-2 (includeTopN=2) in the first week, artist-3 only in the second week -> neither reaches includeMinOccurrences=2
    assertThat(result.map { it.artistId }).containsExactly(ArtistId("artist-2"))
  }

  // --- view repository / outbox handler ---

  @Test
  fun `getSingularitySuggestionsView returns empty view when nothing has been precomputed yet`() {
    every { singularitySuggestionsViewRepository.find() } returns null

    val result = service.getSingularitySuggestionsView()

    assertThat(result).isEqualTo(SingularitySuggestionsView())
  }

  @Test
  fun `getSingularitySuggestionsView returns the precomputed view`() {
    val precomputed = SingularitySuggestionsView(includeCandidates = emptyList(), excludeCandidates = emptyList())
    every { singularitySuggestionsViewRepository.find() } returns precomputed

    val result = service.getSingularitySuggestionsView()

    assertThat(result).isEqualTo(precomputed)
  }

  @Test
  fun `rebuildSingularitySuggestionsView saves freshly computed candidates`() {
    every { appArtistRepository.findAll() } returns emptyList()
    every { aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, any(), any()) } returns emptyList()
    every { singularitySuggestionsViewRepository.save(any()) } just runs

    service.rebuildSingularitySuggestionsView()

    verify { singularitySuggestionsViewRepository.save(SingularitySuggestionsView(includeCandidates = emptyList(), excludeCandidates = emptyList())) }
  }

  @Test
  fun `handle RebuildSingularitySuggestions skips when no user exists`() {
    every { currentUserResolver.userId() } returns null

    val result = service.handle(DomainOutboxEvent.RebuildSingularitySuggestions())

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { singularitySuggestionsViewRepository.save(any()) }
  }

  @Test
  fun `handle RebuildSingularitySuggestions rebuilds the view for the current user`() {
    every { currentUserResolver.userId() } returns userId
    every { appArtistRepository.findAll() } returns emptyList()
    every { aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, any(), any()) } returns emptyList()
    every { singularitySuggestionsViewRepository.save(any()) } just runs

    val result = service.handle(DomainOutboxEvent.RebuildSingularitySuggestions())

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { singularitySuggestionsViewRepository.save(any()) }
  }

  private fun artist(id: String, included: Boolean, currentTrackAddedAt: Instant?) = AppArtist(
    id = ArtistId(id),
    artistName = "Artist $id",
    lastSync = syncTimestamp,
    singularityIncluded = included,
    singularityCurrentTrackAddedAt = currentTrackAddedAt,
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
