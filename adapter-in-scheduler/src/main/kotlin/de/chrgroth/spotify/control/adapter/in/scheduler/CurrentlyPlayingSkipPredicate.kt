package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.quarkus.starters.StarterSkipPredicate
import de.chrgroth.spotify.control.domain.model.PlaybackDetectedEvent
import de.chrgroth.spotify.control.domain.port.out.PlaybackActivityPort
import io.quarkus.scheduler.Scheduled
import io.quarkus.scheduler.ScheduledExecution
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@ApplicationScoped
class CurrentlyPlayingSkipPredicate @JvmOverloads constructor(
    private val starterSkipPredicate: StarterSkipPredicate = StarterSkipPredicate(),
) : Scheduled.SkipPredicate, PlaybackActivityPort {

    private val lastExecutedAtRef = AtomicReference<Instant>(Instant.EPOCH)
    private val lastPlaybackDetectedAtRef = AtomicReference<Instant>(Instant.EPOCH)

    @Suppress("UnusedParameter")
    fun onPlaybackDetected(@Observes event: PlaybackDetectedEvent) {
        lastPlaybackDetectedAtRef.set(Instant.now())
    }

    override fun test(execution: ScheduledExecution): Boolean {
        if (starterSkipPredicate.test(execution)) return true
        val effectiveInterval = if (isPlaybackActive()) FAST_INTERVAL else SLOW_INTERVAL
        val now = Instant.now()
        val lastExecutedAt = lastExecutedAtRef.get()
        if (now.isBefore(lastExecutedAt.plus(effectiveInterval))) return true
        return !lastExecutedAtRef.compareAndSet(lastExecutedAt, now)
    }

    override fun isPlaybackActive(): Boolean =
        Duration.between(lastPlaybackDetectedAtRef.get(), Instant.now()) < SLOW_INTERVAL

    companion object {
        private val FAST_INTERVAL: Duration = Duration.ofSeconds(20)
        private val SLOW_INTERVAL: Duration = Duration.ofMinutes(5)
    }
}
