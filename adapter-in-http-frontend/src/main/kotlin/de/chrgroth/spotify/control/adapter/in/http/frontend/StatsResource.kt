package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n.StatsMessages
import de.chrgroth.spotify.control.domain.model.playback.aggregation.ActivityTimeWindow
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationRankEntry
import de.chrgroth.spotify.control.domain.model.playback.aggregation.PlaybackAggregation
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackAggregationPort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

@Path("/stats")
@ApplicationScoped
@Suppress("Unused")
class StatsResource(
  @param:Location("stats.html")
  private val statsTemplate: Template,
  private val playbackAggregationPort: PlaybackAggregationPort,
  private val httpResponseMetrics: ResponseTimingPort,
  private val messages: StatsMessages,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun stats(@QueryParam("date") dateParam: String?): TemplateInstance = httpResponseMetrics.timed("page.stats.view") {
    val today = currentPeriodStart(AggregationPeriodType.DAY)
    val requestedDate = dateParam?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { if (it > today) today else it }
    val periodStartsByType = AggregationPeriodType.entries.associateWith { type ->
      if (type == AggregationPeriodType.DAY && requestedDate != null) listOf(requestedDate) else threePeriodStarts(type)
    }
    val requestedPeriods = periodStartsByType.flatMap { (type, periodStarts) -> periodStarts.map { type to it } }
    val aggregationsByTypeAndPeriod = playbackAggregationPort.findByPeriods(requestedPeriods, TOP_ENTRIES_LIMIT)
    val tabs = AggregationPeriodType.entries.mapIndexed { index, type ->
      val periodStarts = periodStartsByType.getValue(type)
      AggregationTab(
        id = type.name.lowercase(),
        label = tabLabel(type),
        first = index == 0,
        aggregations = periodStarts.mapIndexed { periodIndex, periodStart ->
          val data = aggregationsByTypeAndPeriod[type to periodStart]
          AggregationView(
            periodLabel = periodLabel(periodIndex, periodStart, singleSelected = periodStarts.size == 1),
            periodStart = periodStart.toString(),
            data = data,
            topArtists = topEntries(data?.artistEntries),
            topAlbums = topEntries(data?.albumEntries),
            topTracks = topEntries(data?.trackEntries),
            activityBars = activityBars(data),
          )
        },
      )
    }
    statsTemplate.instance().data("tabs", tabs)
  }

  private fun topEntries(entries: List<AggregationRankEntry>?): List<RankEntryView> =
    entries.orEmpty().take(TOP_ENTRIES_LIMIT).map { entry ->
      RankEntryView(
        name = entry.name,
        totalSeconds = entry.totalSeconds,
        imageLink = entry.imageLink,
        artistName = entry.artistName,
        albumName = entry.albumName,
        trackDurationMs = entry.trackDurationMs,
      )
    }

  private fun activityBars(aggregation: PlaybackAggregation?): List<ActivityBarEntryView> {
    if (aggregation == null) {
      return emptyList()
    }
    val byKey = aggregation.activityEntries.associate { (it.dayOfWeek to it.timeWindow) to it.totalSeconds }
    val orderedEntries = DayOfWeek.values().flatMap { day ->
      ActivityTimeWindow.entries.map { window ->
        val totalSeconds = byKey[day to window] ?: 0L
        val dayName = weekdayName(day)
        ActivityBarEntryView(
          label = "${dayName.take(WEEKDAY_SHORT_LABEL_LENGTH)} ${window.label}",
          tooltip = messages.statsActivityTooltip(dayName, window.label, TemplateFormattingExtensions.formattedDuration(totalSeconds)),
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
      AggregationPeriodType.WEEK -> javaDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
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
    AggregationPeriodType.DAY -> messages.statsTabDay()
    AggregationPeriodType.WEEK -> messages.statsTabWeek()
    AggregationPeriodType.MONTH -> messages.statsTabMonth()
    AggregationPeriodType.QUARTER -> messages.statsTabQuarter()
    AggregationPeriodType.YEAR -> messages.statsTabYear()
  }

  private fun periodLabel(index: Int, periodStart: LocalDate, singleSelected: Boolean): String {
    if (singleSelected) {
      return messages.statsPeriodSelected(periodStart.toString())
    }
    return when (index) {
      0 -> messages.statsPeriodCurrent(periodStart.toString())
      1 -> messages.statsPeriodPrevious(periodStart.toString())
      else -> messages.statsPeriodTwoAgo(periodStart.toString())
    }
  }

  private fun weekdayName(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> messages.statsWeekdayMonday()
    DayOfWeek.TUESDAY -> messages.statsWeekdayTuesday()
    DayOfWeek.WEDNESDAY -> messages.statsWeekdayWednesday()
    DayOfWeek.THURSDAY -> messages.statsWeekdayThursday()
    DayOfWeek.FRIDAY -> messages.statsWeekdayFriday()
    DayOfWeek.SATURDAY -> messages.statsWeekdaySaturday()
    DayOfWeek.SUNDAY -> messages.statsWeekdaySunday()
  }

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
    private const val WEEKDAY_SHORT_LABEL_LENGTH = 3
  }
}

private val ActivityTimeWindow.label: String
  get() = when (this) {
    ActivityTimeWindow.H00_06 -> "0-6"
    ActivityTimeWindow.H06_12 -> "6-12"
    ActivityTimeWindow.H12_18 -> "12-18"
    ActivityTimeWindow.H18_24 -> "18-0"
  }
