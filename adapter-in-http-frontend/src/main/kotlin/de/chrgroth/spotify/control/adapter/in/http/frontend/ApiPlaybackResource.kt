package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationRankEntry
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackAggregationPort
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackEventViewerPort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toLocalDateTime
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import kotlin.time.Clock

/**
 * Token API (see [ApiTokenAuthMechanism]): playback events and listening stats as JSON, plus enqueueing a playback
 * poll and a profile refresh.
 */
@Path("/api/v1/playback")
@ApplicationScoped
@Suppress("Unused")
class ApiPlaybackResource(
  private val playbackEventViewer: PlaybackEventViewerPort,
  private val playbackAggregation: PlaybackAggregationPort,
  private val playbackSettings: PlaybackSettingsResource,
  private val httpResponseMetrics: ResponseTimingPort,
) {

  @GET
  @Path("/events")
  @RolesAllowed(ApiTokenRoles.READ)
  @Produces(MediaType.APPLICATION_JSON)
  fun events(@QueryParam("date") dateParam: String?): Response = httpResponseMetrics.timed("rest.api.playback.events") {
    val date = parseDate(dateParam) ?: return@timed badRequest("Invalid date '$dateParam', expected YYYY-MM-DD")
    val result = playbackEventViewer.getEvents(date)
    Response.ok(
      mapOf(
        "date" to result.date.toString(),
        "events" to result.events.map {
          mapOf(
            "type" to it.type.name,
            "timestamp" to it.timestamp.toString(),
            "warning" to it.isWarning,
            "trackId" to it.trackId,
            "trackName" to it.trackName,
            "artistId" to it.artistId,
            "artistName" to it.artistName,
            "albumId" to it.albumId,
            "albumName" to it.albumName,
            "startTime" to it.startTime?.toString(),
            "durationSeconds" to it.durationSeconds,
          )
        },
      ),
    ).build()
  }

  /**
   * Aggregated listening stats for the [period] containing [dateParam] (default: today). The date is normalised to
   * the period start, e.g. any date in a week resolves to that week's Monday.
   */
  @GET
  @Path("/stats")
  @RolesAllowed(ApiTokenRoles.READ)
  @Produces(MediaType.APPLICATION_JSON)
  fun stats(
    @QueryParam("period") @DefaultValue("DAY") period: String,
    @QueryParam("date") dateParam: String?,
    @QueryParam("limit") @DefaultValue("10") limit: Int,
  ): Response = httpResponseMetrics.timed("rest.api.playback.stats") {
    val type = AggregationPeriodType.entries.firstOrNull { it.name.equals(period, ignoreCase = true) }
      ?: return@timed badRequest("Invalid period '$period', expected one of ${AggregationPeriodType.entries.joinToString()}")
    val date = parseDate(dateParam) ?: return@timed badRequest("Invalid date '$dateParam', expected YYYY-MM-DD")
    val periodStart = periodStart(type, date)
    val aggregation = playbackAggregation.findByPeriods(listOf(type to periodStart), limit.coerceIn(1, MAX_TOP_ENTRIES))[type to periodStart]
      ?: return@timed Response.status(Response.Status.NOT_FOUND)
        .entity(mapOf("error" to "No aggregation for $type starting $periodStart"))
        .build()
    Response.ok(
      mapOf(
        "period" to type.name,
        "periodStart" to periodStart.toString(),
        "totalPlaybackSeconds" to aggregation.totalPlaybackSeconds,
        "eventCount" to aggregation.eventCount,
        "distinctArtistCount" to aggregation.distinctArtistCount,
        "distinctAlbumCount" to aggregation.distinctAlbumCount,
        "distinctTrackCount" to aggregation.distinctTrackCount,
        "topArtists" to aggregation.artistEntries.map { it.toJson() },
        "topAlbums" to aggregation.albumEntries.map { it.toJson() },
        "topTracks" to aggregation.trackEntries.map { it.toJson() },
        "activity" to aggregation.activityEntries.map {
          mapOf("dayOfWeek" to it.dayOfWeek.name, "timeWindow" to it.timeWindow.name, "totalSeconds" to it.totalSeconds)
        },
      ),
    ).build()
  }

  @POST
  @Path("/poll")
  @RolesAllowed(ApiTokenRoles.WRITE)
  @Produces(MediaType.APPLICATION_JSON)
  fun poll(): Response = playbackSettings.pollNow()

  @POST
  @Path("/profile/refresh")
  @RolesAllowed(ApiTokenRoles.WRITE)
  @Produces(MediaType.APPLICATION_JSON)
  fun refreshProfile(): Response = playbackSettings.refreshProfile()

  private fun parseDate(dateParam: String?): LocalDate? {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    if (dateParam.isNullOrBlank()) return today
    return runCatching { LocalDate.parse(dateParam) }.getOrNull()?.let { if (it > today) today else it }
  }

  private fun periodStart(type: AggregationPeriodType, date: LocalDate): LocalDate {
    val javaDate = date.toJavaLocalDate()
    return when (type) {
      AggregationPeriodType.DAY -> javaDate
      AggregationPeriodType.WEEK -> javaDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
      AggregationPeriodType.MONTH -> javaDate.withDayOfMonth(1)
      AggregationPeriodType.QUARTER -> javaDate.withDayOfMonth(1).withMonth((javaDate.monthValue - 1) / MONTHS_PER_QUARTER * MONTHS_PER_QUARTER + 1)
      AggregationPeriodType.YEAR -> javaDate.withDayOfYear(1)
    }.toKotlinLocalDate()
  }

  private fun AggregationRankEntry.toJson() = mapOf(
    "id" to id,
    "name" to name,
    "artistName" to artistName,
    "albumName" to albumName,
    "totalSeconds" to totalSeconds,
  )

  private fun badRequest(message: String): Response =
    Response.status(Response.Status.BAD_REQUEST).entity(mapOf("error" to message)).build()

  companion object {
    private const val MAX_TOP_ENTRIES = 50
    private const val MONTHS_PER_QUARTER = 3
  }
}
