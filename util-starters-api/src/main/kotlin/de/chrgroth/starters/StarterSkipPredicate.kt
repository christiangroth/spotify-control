package de.chrgroth.starters

import io.quarkus.scheduler.Scheduled
import io.quarkus.scheduler.ScheduledExecution
import jakarta.enterprise.inject.spi.CDI

class StarterSkipPredicate : Scheduled.SkipPredicate {

    override fun test(execution: ScheduledExecution): Boolean =
        !CDI.current().select(StarterCompletionFlag::class.java).get().isCompleted()
}
