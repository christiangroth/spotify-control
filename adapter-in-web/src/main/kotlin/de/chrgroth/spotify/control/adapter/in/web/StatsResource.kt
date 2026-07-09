package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.catalog.displayArtistName
import de.chrgroth.spotify.control.domain.model.playback.aggregation.ActivityTimeWindow
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.model.playback.aggregation.PlaybackAggregation
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackAggregationRepositoryPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
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
  private val aggregationRepository: PlaybackAggregationRepositoryPort,
  private val appTrackRepository: AppTrackRepositoryPort,
  private val appAlbumRepository: AppAlbumRepositoryPort,
  private val appArtistRepository: AppArtistRepositoryPort,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun stats(): TemplateInstance {
    val periodStartsByType = AggregationPeriodType.entries.associateWith { threePeriodStarts(it) }
    val requestedPeriods = periodStartsByType.flatMap { (type, periodStarts) -> periodStarts.map { type to it } }
    val aggregationsByTypeAndPeriod = aggregationRepository.findByPeriods(requestedPeriods)
    val tabs = AggregationPeriodType.entries.mapIndexed { index, type ->
      val periodStarts = periodStartsByType.getValue(type)
      AggregationTab(
        id = type.name.lowercase(),
        label = tabLabel(type),
        first = index == 0,
        aggregations = periodStarts.mapIndexed { periodIndex, periodStart ->
          AggregationView(
            periodLabel = periodLabel(periodIndex, periodStart),
            periodStart = periodStart.toString(),
            data = aggregationsByTypeAndPeriod[type to periodStart],
          )
        },
      )
    }
    val tracksById = loadTracksById(tabs)
    val albumsById = loadAlbumsById(tabs, tracksById)
    val artistsById = loadArtistsById(tabs)
    val renderedTabs = tabs.map { tab ->
      tab.copy(aggregations = tab.aggregations.map { aggregation ->
        aggregation.copy(
          topArtists = topArtists(aggregation.data, artistsById),
          topAlbums = topAlbums(aggregation.data, albumsById),
          topTracks = topTracks(aggregation.data, tracksById, albumsById, artistsById),
          activityBars = activityBars(aggregation.data),
        )
      })
    }
    return statsTemplate.instance().data("tabs", renderedTabs)
  }

  private fun loadTracksById(tabs: List<AggregationTab>) = appTrackRepository.findByTrackIds(
    tabs.flatMap { tab ->
      tab.aggregations.flatMap { aggregation ->
        aggregation.data?.trackEntries?.take(TOP_ENTRIES_LIMIT)?.map { TrackId(it.id) } ?: emptyList()
      }
    }.toSet(),
  ).associateBy { it.id }

  private fun loadAlbumsById(
    tabs: List<AggregationTab>,
    tracksById: Map<TrackId, de.chrgroth.spotify.control.domain.model.catalog.AppTrack>,
  ) = appAlbumRepository.findByAlbumIds(
    (
      tracksById.values.mapNotNull { it.albumId } +
        tabs.flatMap { tab ->
          tab.aggregations.flatMap { aggregation ->
            aggregation.data?.albumEntries?.take(TOP_ENTRIES_LIMIT)?.map { AlbumId(it.id) } ?: emptyList()
          }
        }
      ).toSet(),
  ).associateBy { it.id }

  private fun loadArtistsById(tabs: List<AggregationTab>) = appArtistRepository.findByArtistIds(
    tabs.flatMap { tab ->
      tab.aggregations.flatMap { aggregation ->
        aggregation.data?.artistEntries?.take(TOP_ENTRIES_LIMIT)?.map { ArtistId(it.id) } ?: emptyList()
      }
    }.toSet(),
  ).associateBy { it.id }

  private fun topArtists(
    aggregation: PlaybackAggregation?,
    artistsById: Map<ArtistId, de.chrgroth.spotify.control.domain.model.catalog.AppArtist>,
  ): List<RankEntryView> {
    if (aggregation == null) {
      return emptyList()
    }
    return aggregation.artistEntries
      .take(TOP_ENTRIES_LIMIT)
      .map { entry ->
        val artist = artistsById[ArtistId(entry.id)]
        RankEntryView(name = artist?.artistName ?: entry.name, totalSeconds = entry.totalSeconds, imageLink = artist?.imageLink)
      }
  }

  private fun topAlbums(
    aggregation: PlaybackAggregation?,
    albumsById: Map<AlbumId, de.chrgroth.spotify.control.domain.model.catalog.AppAlbum>,
  ): List<RankEntryView> {
    if (aggregation == null) {
      return emptyList()
    }
    return aggregation.albumEntries
      .take(TOP_ENTRIES_LIMIT)
      .map { entry ->
        val album = albumsById[AlbumId(entry.id)]
        RankEntryView(
          name = album?.title ?: entry.name,
          totalSeconds = entry.totalSeconds,
          imageLink = album?.imageLink,
          artistName = album?.artistName,
        )
      }
  }

  private fun topTracks(
    aggregation: PlaybackAggregation?,
    tracksById: Map<TrackId, de.chrgroth.spotify.control.domain.model.catalog.AppTrack>,
    albumsById: Map<AlbumId, de.chrgroth.spotify.control.domain.model.catalog.AppAlbum>,
    artistsById: Map<ArtistId, de.chrgroth.spotify.control.domain.model.catalog.AppArtist>,
  ): List<RankEntryView> {
    if (aggregation == null) {
      return emptyList()
    }
    return aggregation.trackEntries
      .take(TOP_ENTRIES_LIMIT)
      .map { entry ->
        val track = tracksById[TrackId(entry.id)]
        RankEntryView(
          name = track?.title ?: entry.name,
          totalSeconds = entry.totalSeconds,
          imageLink = track?.albumId?.let { albumsById[it]?.imageLink },
          artistName = track?.displayArtistName { artistId -> artistsById[artistId]?.artistName },
          albumName = track?.albumName ?: track?.albumId?.let { albumsById[it]?.title },
          trackDurationMs = track?.durationMs,
        )
      }
  }

  private fun activityBars(aggregation: PlaybackAggregation?): List<ActivityBarEntryView> {
    if (aggregation == null) {
      return emptyList()
    }
    val byKey = aggregation.activityEntries.associate { (it.dayOfWeek to it.timeWindow) to it.totalSeconds }
    val orderedEntries = DayOfWeek.values().flatMap { day ->
      ActivityTimeWindow.entries.map { window ->
        val totalSeconds = byKey[day to window] ?: 0L
        ActivityBarEntryView(
          label = "${day.shortLabel} ${window.label}",
          tooltip = "${day.name} ${window.label} • ${TemplateFormattingExtensions.formattedDuration(totalSeconds)}",
          totalSeconds = totalSeconds,
        )
      }
    }
    val maxSeconds = orderedEntries.maxOfOrNull { it.totalSeconds } ?: 0L
    return orderedEntries.map { entry ->
      val heightPercent = when {
        maxSeconds <= 0L -> 0
        else -> ((entry.totalSeconds * PERCENT_SCALE) / maxSeconds).toInt()
      }
      entry.copy(
        minHeightPx = if (entry.totalSeconds > 0L) 2 else 0,
        heightPercent = if (entry.totalSeconds > 0L) maxOf(heightPercent, 2) else 0,
      )
    }
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

  data class AggregationTab(val id: String, val label: String, val first: Boolean, val aggregations: List<AggregationView>)

  data class AggregationView(
    val periodLabel: String,
    val periodStart: String,
    val data: PlaybackAggregation?,
    val topArtists: List<RankEntryView> = emptyList(),
    val topAlbums: List<RankEntryView> = emptyList(),
    val topTracks: List<RankEntryView> = emptyList(),
    val activityBars: List<ActivityBarEntryView> = emptyList(),
  )

  data class RankEntryView(
    val name: String,
    val totalSeconds: Long,
    val imageLink: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val trackDurationMs: Long? = null,
  )

  data class ActivityBarEntryView(
    val label: String,
    val tooltip: String,
    val totalSeconds: Long,
    val minHeightPx: Int = 0,
    val heightPercent: Int = 0,
  )

  private fun toJavaLocalDate(date: LocalDate): java.time.LocalDate {
    val monthValue = java.time.Month.valueOf(date.month.name).value
    return java.time.LocalDate.of(date.year, monthValue, date.day)
  }

  private fun calculateQuarterStartMonth(monthValue: Int): Int = ((monthValue - 1) / MONTHS_PER_QUARTER) * MONTHS_PER_QUARTER + 1

  private fun toKotlinLocalDate(date: java.time.LocalDate): LocalDate = LocalDate(date.year, date.monthValue, date.dayOfMonth)

  companion object {
    private const val TOP_ENTRIES_LIMIT = 5
    private const val MONTHS_PER_QUARTER = 3
    private const val PERCENT_SCALE = 100L
  }
}

private val DayOfWeek.shortLabel: String
  get() = when (this) {
    DayOfWeek.MONDAY -> "Mon"
    DayOfWeek.TUESDAY -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY -> "Thu"
    DayOfWeek.FRIDAY -> "Fri"
    DayOfWeek.SATURDAY -> "Sat"
    DayOfWeek.SUNDAY -> "Sun"
  }

private val ActivityTimeWindow.label: String
  get() = when (this) {
    ActivityTimeWindow.H00_06 -> "0-6"
    ActivityTimeWindow.H06_12 -> "6-12"
    ActivityTimeWindow.H12_18 -> "12-18"
    ActivityTimeWindow.H18_24 -> "18-0"
  }
