package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.quarkus.starters.StarterSkipPredicate
import de.chrgroth.spotify.control.adapter.out.scheduler.CurrentlyPlayingScheduleState
import io.quarkus.scheduler.Scheduled
import io.quarkus.scheduler.ScheduledExecution
import jakarta.enterprise.context.ApplicationScoped
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@ApplicationScoped
class CurrentlyPlayingSkipPredicate(
    private val scheduleState: CurrentlyPlayingScheduleState,
    private val starterSkipPredicate: StarterSkipPredicate,
) : Scheduled.SkipPredicate {

    private val lastExecutedAtRef = AtomicReference<Instant>(Instant.EPOCH)

    override fun test(execution: ScheduledExecution): Boolean {
        if (starterSkipPredicate.test(execution)) return true
        val effectiveInterval = if (scheduleState.isPlaybackActive()) FAST_INTERVAL else SLOW_INTERVAL
        val now = Instant.now()
        val lastExecutedAt = lastExecutedAtRef.get()
        if (now.isBefore(lastExecutedAt.plus(effectiveInterval))) return true
        return !lastExecutedAtRef.compareAndSet(lastExecutedAt, now)
    }

    companion object {
        private val FAST_INTERVAL: Duration = Duration.ofSeconds(10)
        private val SLOW_INTERVAL: Duration = CurrentlyPlayingScheduleState.SLOW_INTERVAL
    }
}
