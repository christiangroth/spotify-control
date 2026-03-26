package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.quarkus.starters.domain.ScheduledSkipPredicate
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces

@ApplicationScoped
class ScheduledSkipPredicateProducer {

    @Produces
    @ApplicationScoped
    fun produce(): ScheduledSkipPredicate = ScheduledSkipPredicate()
}
