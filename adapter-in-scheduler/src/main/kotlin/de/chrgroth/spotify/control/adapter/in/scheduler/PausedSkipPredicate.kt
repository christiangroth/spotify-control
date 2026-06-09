package de.chrgroth.spotify.control.adapter.`in`.scheduler

import io.quarkus.scheduler.Scheduled
import io.quarkus.scheduler.ScheduledExecution
import jakarta.enterprise.context.ApplicationScoped

/**
 * A [Scheduled.SkipPredicate] that always skips execution, effectively pausing any scheduled job that uses it.
 * Use this to temporarily disable a job while keeping its cron configuration intact for easy re-activation.
 */
@ApplicationScoped
class PausedSkipPredicate : Scheduled.SkipPredicate {
  override fun test(execution: ScheduledExecution): Boolean = true
}
