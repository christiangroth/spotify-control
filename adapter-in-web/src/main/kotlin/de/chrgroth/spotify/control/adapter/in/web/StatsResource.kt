package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.model.playback.aggregation.PlaybackAggregation
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackAggregationRepositoryPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import java.time.DayOfWeek

@Path("/stats")
@ApplicationScoped
@Suppress("Unused")
class StatsResource(
  @param:Location("stats.html")
  private val statsTemplate: Template,
  private val securityIdentity: SecurityIdentity,
  private val aggregationRepository: PlaybackAggregationRepositoryPort,
  private val appTrackRepository: AppTrackRepositoryPort,
  private val appAlbumRepository: AppAlbumRepositoryPort,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun stats(): TemplateInstance {
    val userId = UserId(securityIdentity.principal.name)
    val tabs = AggregationPeriodType.entries.map { type ->
      val periodStarts = threePeriodStarts(type)
      val aggregationsByStart = periodStarts.associateWith { periodStart ->
        aggregationRepository.findByUserAndPeriod(userId, type, periodStart)
      }
      AggregationTab(
        id = type.name.lowercase(),
        label = tabLabel(type),
        aggregations = periodStarts.mapIndexed { index, periodStart ->
          AggregationView(
            periodLabel = periodLabel(index, periodStart),
            periodStart = periodStart.toString(),
            data = aggregationsByStart[periodStart],
          )
        },
      )
    }
    val tracksById = loadTracksById(tabs)
    val albumsById = loadAlbumsById(tracksById)
    val renderedTabs = tabs.map { tab ->
      tab.copy(aggregations = tab.aggregations.map { aggregation ->
        aggregation.copy(
          topArtists = aggregation.data?.artistEntries?.take(TOP_ENTRIES_LIMIT)?.map { RankEntryView(name = it.name, totalSeconds = it.totalSeconds) } ?: emptyList(),
          topAlbums = topAlbums(aggregation.data, tracksById, albumsById),
          topTracks = aggregation.data?.trackEntries?.take(TOP_ENTRIES_LIMIT)?.map { RankEntryView(name = it.name, totalSeconds = it.totalSeconds) } ?: emptyList(),
        )
      })
    }
    return statsTemplate.instance().data("tabs", renderedTabs)
  }

  private fun loadTracksById(tabs: List<AggregationTab>) = appTrackRepository.findByTrackIds(
    tabs.flatMap { tab ->
      tab.aggregations.flatMap { aggregation ->
        aggregation.data?.trackEntries?.map { TrackId(it.id) } ?: emptyList()
      }
    }.toSet(),
  ).associateBy { it.id }

  private fun loadAlbumsById(tracksById: Map<TrackId, de.chrgroth.spotify.control.domain.model.catalog.AppTrack>) = appAlbumRepository.findByAlbumIds(
    tracksById.values.mapNotNull { it.albumId }.toSet(),
  ).associateBy { it.id }

  private fun topAlbums(
    aggregation: PlaybackAggregation?,
    tracksById: Map<TrackId, de.chrgroth.spotify.control.domain.model.catalog.AppTrack>,
    albumsById: Map<AlbumId, de.chrgroth.spotify.control.domain.model.catalog.AppAlbum>,
  ): List<RankEntryView> {
    if (aggregation == null) {
      return emptyList()
    }
    val byAlbum = linkedMapOf<String, Long>()
    val albumNames = mutableMapOf<String, String>()
    aggregation.trackEntries.forEach { trackEntry ->
      val track = tracksById[TrackId(trackEntry.id)]
      val albumKey = track?.albumId?.value ?: "$FALLBACK_ALBUM_KEY_PREFIX${track?.albumName ?: trackEntry.id}"
      val albumTitle = track?.albumId?.let { albumsById[it]?.title } ?: track?.albumName ?: trackEntry.name
      albumNames.putIfAbsent(albumKey, albumTitle)
      byAlbum[albumKey] = (byAlbum[albumKey] ?: 0L) + trackEntry.totalSeconds
    }
    return byAlbum.entries
      .sortedByDescending { it.value }
      .take(TOP_ENTRIES_LIMIT)
      .map { RankEntryView(name = albumNames[it.key] ?: it.key, totalSeconds = it.value) }
  }

  private fun threePeriodStarts(type: AggregationPeriodType): List<LocalDate> {
    val current = currentPeriodStart(type)
    return listOf(current, previousPeriodStart(type, current), previousPeriodStart(type, previousPeriodStart(type, current)))
  }

  private fun currentPeriodStart(type: AggregationPeriodType): LocalDate {
    val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
    val javaDate = toJavaLocalDate(today)
    val periodStart = when (type) {
      AggregationPeriodType.DAY -> javaDate
      AggregationPeriodType.WEEK -> javaDate.minusDays((javaDate.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
      AggregationPeriodType.MONTH -> javaDate.withDayOfMonth(1)
      AggregationPeriodType.QUARTER -> javaDate.withDayOfMonth(1).withMonth(calculateQuarterStartMonth(javaDate.monthValue))
      AggregationPeriodType.YEAR -> javaDate.withDayOfYear(1)
    }
    return toKotlinLocalDate(periodStart)
  }

  private fun previousPeriodStart(type: AggregationPeriodType, current: LocalDate): LocalDate {
    val javaDate = toJavaLocalDate(current)
    val previousStart = when (type) {
      AggregationPeriodType.DAY -> javaDate.minusDays(1)
      AggregationPeriodType.WEEK -> javaDate.minusWeeks(1)
      AggregationPeriodType.MONTH -> javaDate.minusMonths(1).withDayOfMonth(1)
      AggregationPeriodType.QUARTER -> javaDate.minusMonths(MONTHS_PER_QUARTER.toLong()).withDayOfMonth(1)
      AggregationPeriodType.YEAR -> javaDate.minusYears(1).withDayOfYear(1)
    }
    return toKotlinLocalDate(previousStart)
  }

  private fun tabLabel(type: AggregationPeriodType): String = when (type) {
    AggregationPeriodType.DAY -> "Day"
    AggregationPeriodType.WEEK -> "Week"
    AggregationPeriodType.MONTH -> "Month"
    AggregationPeriodType.QUARTER -> "Quarter"
    AggregationPeriodType.YEAR -> "Year"
  }

  private fun periodLabel(index: Int, periodStart: LocalDate): String = when (index) {
    0 -> "Current"
    1 -> "Previous"
    else -> "Two periods ago"
  } + " ($periodStart)"

  data class AggregationTab(val id: String, val label: String, val aggregations: List<AggregationView>)

  data class AggregationView(
    val periodLabel: String,
    val periodStart: String,
    val data: PlaybackAggregation?,
    val topArtists: List<RankEntryView> = emptyList(),
    val topAlbums: List<RankEntryView> = emptyList(),
    val topTracks: List<RankEntryView> = emptyList(),
  )

  data class RankEntryView(val name: String, val totalSeconds: Long)

  private fun toJavaLocalDate(date: LocalDate): java.time.LocalDate {
    val monthValue = java.time.Month.valueOf(date.month.name).value
    return java.time.LocalDate.of(date.year, monthValue, date.day)
  }

  private fun calculateQuarterStartMonth(monthValue: Int): Int = ((monthValue - 1) / MONTHS_PER_QUARTER) * MONTHS_PER_QUARTER + 1

  private fun toKotlinLocalDate(date: java.time.LocalDate): LocalDate = LocalDate(date.year, date.monthValue, date.dayOfMonth)

  companion object {
    private const val TOP_ENTRIES_LIMIT = 3
    private const val MONTHS_PER_QUARTER = 3
    private const val FALLBACK_ALBUM_KEY_PREFIX = "fallback:"
  }
}
