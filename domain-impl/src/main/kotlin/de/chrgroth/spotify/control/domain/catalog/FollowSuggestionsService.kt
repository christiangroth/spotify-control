package de.chrgroth.spotify.control.domain.catalog

import arrow.core.Either
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.FollowCandidate
import de.chrgroth.spotify.control.domain.model.catalog.FollowSuggestionsView
import de.chrgroth.spotify.control.domain.model.catalog.UnfollowCandidate
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.catalog.FollowSuggestionsPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackAggregationRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.readmodel.FollowSuggestionsViewRepositoryPort
import de.chrgroth.spotify.control.domain.user.CurrentUserResolver
import jakarta.enterprise.context.ApplicationScoped
import java.time.DayOfWeek
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.LocalDate as JLocalDate

/**
 * Scores followed/not-yet-followed artists as unfollow/follow candidates, purely from already persisted
 * WEEK [de.chrgroth.spotify.control.domain.model.playback.aggregation.PlaybackAggregation] rollups. Those
 * rollups already group plays by the main artist only (see [de.chrgroth.spotify.control.domain.model.catalog.AppTrack.artistId]),
 * so no further main-artist/feature-artist filtering is needed here.
 */
@ApplicationScoped
@Suppress("Unused")
class FollowSuggestionsService(
  private val appArtistRepository: AppArtistRepositoryPort,
  private val aggregationRepository: PlaybackAggregationRepositoryPort,
  private val followSuggestionsViewRepository: FollowSuggestionsViewRepositoryPort,
  private val currentUserResolver: CurrentUserResolver,
  @param:ConfigProperty(name = "app.followed-artists.unfollow-lookback-days", defaultValue = "90")
  private val unfollowLookbackDays: Long,
  @param:ConfigProperty(name = "app.followed-artists.unfollow-min-age-days", defaultValue = "30")
  private val unfollowMinAgeDays: Long,
  @param:ConfigProperty(name = "app.followed-artists.unfollow-max-playback-seconds", defaultValue = "0")
  private val unfollowMaxPlaybackSeconds: Long,
  @param:ConfigProperty(name = "app.followed-artists.follow-lookback-weeks", defaultValue = "4")
  private val followLookbackWeeks: Long,
  @param:ConfigProperty(name = "app.followed-artists.follow-top-n", defaultValue = "20")
  private val followTopN: Int,
  @param:ConfigProperty(name = "app.followed-artists.follow-min-occurrences", defaultValue = "2")
  private val followMinOccurrences: Int,
) : FollowSuggestionsPort {

  override fun getFollowSuggestionsView(): FollowSuggestionsView =
    followSuggestionsViewRepository.find() ?: FollowSuggestionsView()

  override fun rebuildFollowSuggestionsView() {
    followSuggestionsViewRepository.save(FollowSuggestionsView(findFollowCandidates(), findUnfollowCandidates()))
  }

  override fun handle(event: DomainOutboxEvent.RebuildFollowSuggestions): Either<DomainError, Unit> {
    currentUserResolver.userId() ?: return Unit.right()
    rebuildFollowSuggestionsView()
    return Unit.right()
  }

  override fun findUnfollowCandidates(): List<UnfollowCandidate> {
    val maxFollowedSince = Clock.System.now().minus(unfollowMinAgeDays.days)
    val eligibleArtists = appArtistRepository.findFollowed().filter { (it.followedSince ?: return@filter false) <= maxFollowedSince }
    if (eligibleArtists.isEmpty()) {
      return emptyList()
    }

    val playbackSecondsByArtistId = playbackSecondsByArtistId(unfollowLookbackStart())

    return eligibleArtists
      .map { artist ->
        UnfollowCandidate(
          artistId = artist.id,
          artistName = artist.artistName,
          imageLink = artist.imageLink,
          followedSince = artist.followedSince,
          lookbackPlaybackSeconds = playbackSecondsByArtistId[artist.id.value] ?: 0L,
        )
      }
      .filter { it.lookbackPlaybackSeconds <= unfollowMaxPlaybackSeconds }
      .sortedBy { it.lookbackPlaybackSeconds }
  }

  override fun findFollowCandidates(): List<FollowCandidate> {
    val followedIds = appArtistRepository.findFollowed().map { it.id }.toSet()
    val weeks = aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, followLookbackStart(), lastCompleteWeekStart())

    val topRankWeeksByArtistId = mutableMapOf<String, Int>()
    val playbackSecondsByArtistId = mutableMapOf<String, Long>()
    val nameByArtistId = mutableMapOf<String, String>()
    val imageLinkByArtistId = mutableMapOf<String, String?>()
    weeks.forEach { week ->
      week.artistEntries.take(followTopN).forEach { entry ->
        topRankWeeksByArtistId[entry.id] = (topRankWeeksByArtistId[entry.id] ?: 0) + 1
        playbackSecondsByArtistId[entry.id] = (playbackSecondsByArtistId[entry.id] ?: 0L) + entry.totalSeconds
        nameByArtistId.putIfAbsent(entry.id, entry.name)
        imageLinkByArtistId.putIfAbsent(entry.id, entry.imageLink)
      }
    }

    return topRankWeeksByArtistId
      .filter { (artistId, topRankWeeks) -> topRankWeeks >= followMinOccurrences && ArtistId(artistId) !in followedIds }
      .map { (artistId, topRankWeeks) ->
        FollowCandidate(
          artistId = ArtistId(artistId),
          artistName = nameByArtistId.getValue(artistId),
          imageLink = imageLinkByArtistId[artistId],
          recentPlaybackSeconds = playbackSecondsByArtistId.getValue(artistId),
          topRankWeeks = topRankWeeks,
        )
      }
      .sortedByDescending { it.recentPlaybackSeconds }
  }

  private fun playbackSecondsByArtistId(from: LocalDate): Map<String, Long> {
    val weeks = aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, from, lastCompleteWeekStart())
    val playbackSecondsByArtistId = mutableMapOf<String, Long>()
    weeks.forEach { week ->
      week.artistEntries.forEach { entry ->
        playbackSecondsByArtistId[entry.id] = (playbackSecondsByArtistId[entry.id] ?: 0L) + entry.totalSeconds
      }
    }
    return playbackSecondsByArtistId
  }

  private fun unfollowLookbackStart(): LocalDate =
    JLocalDate.now(ZoneOffset.UTC).minusDays(unfollowLookbackDays).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toKotlin()

  private fun followLookbackStart(): LocalDate =
    lastCompleteWeekStart().toJavaLocalDate().minusWeeks(followLookbackWeeks - 1).toKotlin()

  private fun lastCompleteWeekStart(): LocalDate =
    JLocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1).toKotlin()

  private fun JLocalDate.toKotlin(): LocalDate = LocalDate(year, monthValue, dayOfMonth)
}
