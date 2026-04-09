package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.quarkus.starters.domain.Starter
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackAggregationPort
import de.chrgroth.spotify.control.domain.port.out.playback.AppPlaybackRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import java.time.DayOfWeek
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import mu.KLogging
import java.time.LocalDate as JLocalDate

@ApplicationScoped
@Suppress("Unused")
class BackfillPlaybackAggregationsStarter(
  private val appPlaybackRepository: AppPlaybackRepositoryPort,
  private val playbackAggregation: PlaybackAggregationPort,
) : Starter {

  override val id = "BackfillPlaybackAggregationsStarter-v1"

  override fun execute() {
    val oldestInstant = appPlaybackRepository.findOldestPlayedAt()
    if (oldestInstant == null) {
      logger.info { "No playback data found, skipping backfill" }
      return
    }

    val firstDate = JLocalDate.ofInstant(oldestInstant.toJavaInstant(), ZoneOffset.UTC)
    val today = JLocalDate.now(ZoneOffset.UTC)
    val yesterday = today.minusDays(1)

    logger.info { "Starting playback aggregation backfill from $firstDate to $yesterday" }

    enqueueDays(firstDate, yesterday)
    enqueueWeeks(firstDate, yesterday)
    enqueueMonths(firstDate, yesterday)
    enqueueQuarters(firstDate, yesterday)
    enqueueYears(firstDate, yesterday)

    logger.info { "Backfill aggregation enqueuing complete" }
  }

  private fun enqueueDays(from: JLocalDate, to: JLocalDate) {
    var date = from
    while (!date.isAfter(to)) {
      playbackAggregation.enqueueAggregateDay(date.toKotlin())
      date = date.plusDays(1)
    }
    logger.info { "Enqueued daily aggregations from $from to $to" }
  }

  private fun enqueueWeeks(from: JLocalDate, to: JLocalDate) {
    var weekStart = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val lastCompleteWeekStart = to.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    var count = 0
    while (!weekStart.isAfter(lastCompleteWeekStart)) {
      playbackAggregation.enqueueAggregateWeek(weekStart.toKotlin())
      weekStart = weekStart.plusWeeks(1)
      count++
    }
    logger.info { "Enqueued $count weekly aggregation(s)" }
  }

  private fun enqueueMonths(from: JLocalDate, to: JLocalDate) {
    var monthStart = from.withDayOfMonth(1)
    val lastCompleteMonthStart = to.withDayOfMonth(1)
    var count = 0
    while (!monthStart.isAfter(lastCompleteMonthStart)) {
      playbackAggregation.enqueueAggregateMonth(monthStart.toKotlin())
      monthStart = monthStart.plusMonths(1)
      count++
    }
    logger.info { "Enqueued $count monthly aggregation(s)" }
  }

  private fun enqueueQuarters(from: JLocalDate, to: JLocalDate) {
    var quarterStart = firstDayOfQuarter(from)
    val lastCompleteQuarterStart = firstDayOfQuarter(to)
    var count = 0
    while (!quarterStart.isAfter(lastCompleteQuarterStart)) {
      playbackAggregation.enqueueAggregateQuarter(quarterStart.toKotlin())
      quarterStart = quarterStart.plusMonths(3)
      count++
    }
    logger.info { "Enqueued $count quarterly aggregation(s)" }
  }

  private fun enqueueYears(from: JLocalDate, to: JLocalDate) {
    var yearStart = from.withDayOfYear(1)
    val lastCompleteYearStart = to.withDayOfYear(1)
    var count = 0
    while (!yearStart.isAfter(lastCompleteYearStart)) {
      playbackAggregation.enqueueAggregateYear(yearStart.toKotlin())
      yearStart = yearStart.plusYears(1)
      count++
    }
    logger.info { "Enqueued $count yearly aggregation(s)" }
  }

  private fun firstDayOfQuarter(date: JLocalDate): JLocalDate {
    val firstMonthOfQuarter = ((date.monthValue - 1) / 3) * 3 + 1
    return JLocalDate.of(date.year, firstMonthOfQuarter, 1)
  }

  companion object : KLogging()
}

private fun JLocalDate.toKotlin(): LocalDate = toKotlinLocalDate()

private fun kotlin.time.Instant.toJavaInstant(): java.time.Instant =
  java.time.Instant.ofEpochMilli(toEpochMilliseconds())
