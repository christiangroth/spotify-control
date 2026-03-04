package de.chrgroth.spotify.control.util.starters

import io.quarkus.scheduler.Scheduled
import io.quarkus.scheduler.ScheduledExecution
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class StarterSkipPredicate(
    private val completionFlag: StarterCompletionFlag,
) : Scheduled.SkipPredicate {

    override fun test(execution: ScheduledExecution): Boolean = !completionFlag.isCompleted()
}
