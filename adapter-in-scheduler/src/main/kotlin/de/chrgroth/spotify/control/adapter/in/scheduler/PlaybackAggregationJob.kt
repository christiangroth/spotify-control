package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.quarkus.starters.domain.ScheduledSkipPredicate
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackAggregationPort
import io.micrometer.core.annotation.Timed
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.datetime.LocalDate as KLocalDate

@ApplicationScoped
@Suppress("Unused")
class PlaybackAggregationJob(
  private val aggregation: PlaybackAggregationPort,
) {

  @Timed(value = "scheduler.job", extraTags = ["invoker", "PlaybackAggregationJob.aggregateDaily"], histogram = true)
  @Scheduled(cron = "0 0 1 * * ?", skipExecutionIf = ScheduledSkipPredicate::class)
  fun aggregateDaily() {
    aggregation.enqueueAggregateDay(LocalDate.now(ZoneOffset.UTC).minusDays(1).toKotlin())
  }

  @Timed(value = "scheduler.job", extraTags = ["invoker", "PlaybackAggregationJob.aggregateWeekly"], histogram = true)
  @Scheduled(cron = "0 30 1 ? * MON", skipExecutionIf = ScheduledSkipPredicate::class)
  fun aggregateWeekly() {
    aggregation.enqueueAggregateWeek(LocalDate.now(ZoneOffset.UTC).minusWeeks(1).toKotlin())
  }

  @Timed(value = "scheduler.job", extraTags = ["invoker", "PlaybackAggregationJob.aggregateMonthly"], histogram = true)
  @Scheduled(cron = "0 0 2 1 * ?", skipExecutionIf = ScheduledSkipPredicate::class)
  fun aggregateMonthly() {
    aggregation.enqueueAggregateMonth(LocalDate.now(ZoneOffset.UTC).minusMonths(1).withDayOfMonth(1).toKotlin())
  }

  @Timed(value = "scheduler.job", extraTags = ["invoker", "PlaybackAggregationJob.aggregateQuarterly"], histogram = true)
  @Scheduled(cron = "0 30 2 1 1,4,7,10 ?", skipExecutionIf = ScheduledSkipPredicate::class)
  fun aggregateQuarterly() {
    aggregation.enqueueAggregateQuarter(LocalDate.now(ZoneOffset.UTC).minusMonths(MONTHS_PER_QUARTER).withDayOfMonth(1).toKotlin())
  }

  @Timed(value = "scheduler.job", extraTags = ["invoker", "PlaybackAggregationJob.aggregateYearly"], histogram = true)
  @Scheduled(cron = "0 0 3 1 1 ?", skipExecutionIf = ScheduledSkipPredicate::class)
  fun aggregateYearly() {
    aggregation.enqueueAggregateYear(LocalDate.now(ZoneOffset.UTC).minusYears(1).withDayOfMonth(1).toKotlin())
  }

  companion object {
    private const val MONTHS_PER_QUARTER = 3L
  }
}

private fun LocalDate.toKotlin(): KLocalDate = KLocalDate(year, monthValue, dayOfMonth)
