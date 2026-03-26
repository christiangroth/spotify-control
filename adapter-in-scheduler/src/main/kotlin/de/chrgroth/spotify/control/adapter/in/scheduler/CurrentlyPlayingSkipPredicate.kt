package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.quarkus.starters.domain.ScheduledSkipPredicate
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackActivityPort
import io.quarkus.scheduler.Scheduled
import io.quarkus.scheduler.ScheduledExecution
import jakarta.enterprise.context.ApplicationScoped
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@ApplicationScoped
class CurrentlyPlayingSkipPredicate(
    private val starterSkipPredicate: ScheduledSkipPredicate,
    private val playbackActivity: PlaybackActivityPort,
) : Scheduled.SkipPredicate {

    private val lastExecutedAtRef = AtomicReference<Instant>(Instant.EPOCH)

    override fun test(execution: ScheduledExecution): Boolean {
        if (starterSkipPredicate.test(execution)) return true
        val effectiveInterval = if (playbackActivity.isPlaybackActive()) FAST_INTERVAL else SLOW_INTERVAL
        val now = Instant.now()
        val lastExecutedAt = lastExecutedAtRef.get()
        if (now.isBefore(lastExecutedAt.plus(effectiveInterval))) return true
        return !lastExecutedAtRef.compareAndSet(lastExecutedAt, now)
    }

    companion object {
        private val FAST_INTERVAL: Duration = Duration.ofSeconds(20)
        private val SLOW_INTERVAL: Duration = Duration.ofMinutes(5)
    }
}
