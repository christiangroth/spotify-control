package de.chrgroth.spotify.control.domain.playlist

import arrow.core.Either
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.model.playlist.SingularityExcludeCandidate
import de.chrgroth.spotify.control.domain.model.playlist.SingularityIncludeCandidate
import de.chrgroth.spotify.control.domain.model.playlist.SingularitySuggestionsView
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.playlist.SingularitySuggestionsPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackAggregationRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.readmodel.SingularitySuggestionsViewRepositoryPort
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
 * Scores singularity-included/not-yet-included artists as exclude/include candidates for "End of the Road", purely
 * from already persisted WEEK [de.chrgroth.spotify.control.domain.model.playback.aggregation.PlaybackAggregation]
 * rollups. Deliberately a standalone service rather than shared with
 * [de.chrgroth.spotify.control.domain.catalog.FollowSuggestionsService], even though both mine the same rollups:
 * followed-status and singularity-inclusion are independent axes (see #941). Purely a read-only suggestion list;
 * it never writes AppArtist.singularityIncluded itself.
 */
@ApplicationScoped
@Suppress("Unused")
class SingularitySuggestionsService(
  private val appArtistRepository: AppArtistRepositoryPort,
  private val aggregationRepository: PlaybackAggregationRepositoryPort,
  private val singularitySuggestionsViewRepository: SingularitySuggestionsViewRepositoryPort,
  private val currentUserResolver: CurrentUserResolver,
  @param:ConfigProperty(name = "app.singularity-artists.exclude-lookback-days", defaultValue = "90")
  private val excludeLookbackDays: Long,
  @param:ConfigProperty(name = "app.singularity-artists.exclude-min-age-days", defaultValue = "30")
  private val excludeMinAgeDays: Long,
  @param:ConfigProperty(name = "app.singularity-artists.exclude-max-playback-seconds", defaultValue = "0")
  private val excludeMaxPlaybackSeconds: Long,
  @param:ConfigProperty(name = "app.singularity-artists.include-lookback-weeks", defaultValue = "4")
  private val includeLookbackWeeks: Long,
  @param:ConfigProperty(name = "app.singularity-artists.include-top-n", defaultValue = "20")
  private val includeTopN: Int,
  @param:ConfigProperty(name = "app.singularity-artists.include-min-occurrences", defaultValue = "2")
  private val includeMinOccurrences: Int,
) : SingularitySuggestionsPort {

  override fun getSingularitySuggestionsView(): SingularitySuggestionsView =
    singularitySuggestionsViewRepository.find() ?: SingularitySuggestionsView()

  override fun rebuildSingularitySuggestionsView() {
    singularitySuggestionsViewRepository.save(SingularitySuggestionsView(findIncludeCandidates(), findExcludeCandidates()))
  }

  override fun handle(event: DomainOutboxEvent.RebuildSingularitySuggestions): Either<DomainError, Unit> {
    currentUserResolver.userId() ?: return Unit.right()
    rebuildSingularitySuggestionsView()
    return Unit.right()
  }

  override fun findExcludeCandidates(): List<SingularityExcludeCandidate> {
    val maxCurrentTrackAddedAt = Clock.System.now().minus(excludeMinAgeDays.days)
    val eligibleArtists = appArtistRepository.findAll()
      .filter { it.singularityIncluded }
      .filter { (it.singularityCurrentTrackAddedAt ?: return@filter false) <= maxCurrentTrackAddedAt }
    if (eligibleArtists.isEmpty()) {
      return emptyList()
    }

    val playbackSecondsByArtistId = playbackSecondsByArtistId(excludeLookbackStart())

    return eligibleArtists
      .map { artist ->
        SingularityExcludeCandidate(
          artistId = artist.id,
          artistName = artist.artistName,
          imageLink = artist.imageLink,
          currentTrackAddedAt = artist.singularityCurrentTrackAddedAt,
          lookbackPlaybackSeconds = playbackSecondsByArtistId[artist.id.value] ?: 0L,
        )
      }
      .filter { it.lookbackPlaybackSeconds <= excludeMaxPlaybackSeconds }
      .sortedBy { it.lookbackPlaybackSeconds }
  }

  override fun findIncludeCandidates(): List<SingularityIncludeCandidate> {
    val includedIds = appArtistRepository.findAll().filter { it.singularityIncluded }.map { it.id }.toSet()
    val weeks = aggregationRepository.findByTypeAndPeriodRange(AggregationPeriodType.WEEK, includeLookbackStart(), lastCompleteWeekStart())

    val topRankWeeksByArtistId = mutableMapOf<String, Int>()
    val playbackSecondsByArtistId = mutableMapOf<String, Long>()
    val nameByArtistId = mutableMapOf<String, String>()
    val imageLinkByArtistId = mutableMapOf<String, String?>()
    weeks.forEach { week ->
      week.artistEntries.take(includeTopN).forEach { entry ->
        topRankWeeksByArtistId[entry.id] = (topRankWeeksByArtistId[entry.id] ?: 0) + 1
        playbackSecondsByArtistId[entry.id] = (playbackSecondsByArtistId[entry.id] ?: 0L) + entry.totalSeconds
        nameByArtistId.putIfAbsent(entry.id, entry.name)
        imageLinkByArtistId.putIfAbsent(entry.id, entry.imageLink)
      }
    }

    return topRankWeeksByArtistId
      .filter { (artistId, topRankWeeks) -> topRankWeeks >= includeMinOccurrences && ArtistId(artistId) !in includedIds }
      .map { (artistId, topRankWeeks) ->
        SingularityIncludeCandidate(
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

  private fun excludeLookbackStart(): LocalDate =
    JLocalDate.now(ZoneOffset.UTC).minusDays(excludeLookbackDays).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toKotlin()

  private fun includeLookbackStart(): LocalDate =
    lastCompleteWeekStart().toJavaLocalDate().minusWeeks(includeLookbackWeeks - 1).toKotlin()

  private fun lastCompleteWeekStart(): LocalDate =
    JLocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1).toKotlin()

  private fun JLocalDate.toKotlin(): LocalDate = LocalDate(year, monthValue, dayOfMonth)
}
