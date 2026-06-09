package de.chrgroth.spotify.control.adapter.`in`.scheduler

import io.quarkus.scheduler.Scheduled
import io.quarkus.scheduler.ScheduledExecution
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class PausedSkipPredicate : Scheduled.SkipPredicate {
  override fun test(execution: ScheduledExecution): Boolean = true
}
