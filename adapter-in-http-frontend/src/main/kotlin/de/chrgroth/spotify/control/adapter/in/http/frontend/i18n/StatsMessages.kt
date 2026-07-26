package de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * UI strings specific to the stats page (`templates/stats.html`). Shared/reusable app-shell strings live in
 * [AppMessages] instead.
 */
@MessageBundle("stats")
interface StatsMessages {

  @Message
  fun statsTitle(): String

  // aggregation rebuild
  @Message
  fun statsAggregationsHeading(): String

  @Message
  fun statsAggregationsDescription(): String

  @Message
  fun statsRebuildAggregationsButton(): String

  @Message
  fun statsRebuildAggregationsSuccess(): String

  @Message
  fun statsRebuildAggregationsErrorPrefix(): String

  // period tabs
  @Message
  fun statsTabDay(): String

  @Message
  fun statsTabWeek(): String

  @Message
  fun statsTabMonth(): String

  @Message
  fun statsTabQuarter(): String

  @Message
  fun statsTabYear(): String

  // period labels
  @Message("Selected ({periodStart})")
  fun statsPeriodSelected(periodStart: String): String

  @Message("Current ({periodStart})")
  fun statsPeriodCurrent(periodStart: String): String

  @Message("Previous ({periodStart})")
  fun statsPeriodPrevious(periodStart: String): String

  @Message("Two periods ago ({periodStart})")
  fun statsPeriodTwoAgo(periodStart: String): String

  @Message
  fun statsViewPlaybackEventsLink(): String

  // aggregation summary
  @Message
  fun statsListenedLabel(): String

  @Message("({count} Events)")
  fun statsEventsCountSuffix(count: String): String

  @Message("Top Tracks ({current}/{total})")
  fun statsTopTracksLabel(current: String, total: String): String

  @Message
  fun statsTopTracksTitle(): String

  @Message("Top Albums ({current}/{total})")
  fun statsTopAlbumsLabel(current: String, total: String): String

  @Message
  fun statsTopAlbumsTitle(): String

  @Message("Top Artists ({current}/{total})")
  fun statsTopArtistsLabel(current: String, total: String): String

  @Message
  fun statsTopArtistsTitle(): String

  @Message
  fun statsNoData(): String

  @Message
  fun statsNoAggregationDataMessage(): String

  // activity chart
  @Message
  fun statsActivityHeading(): String

  @Message("{day} {window} • {duration}")
  fun statsActivityTooltip(day: String, window: String, duration: String): String

  @Message
  fun statsWeekdayMonday(): String

  @Message
  fun statsWeekdayTuesday(): String

  @Message
  fun statsWeekdayWednesday(): String

  @Message
  fun statsWeekdayThursday(): String

  @Message
  fun statsWeekdayFriday(): String

  @Message
  fun statsWeekdaySaturday(): String

  @Message
  fun statsWeekdaySunday(): String
}
