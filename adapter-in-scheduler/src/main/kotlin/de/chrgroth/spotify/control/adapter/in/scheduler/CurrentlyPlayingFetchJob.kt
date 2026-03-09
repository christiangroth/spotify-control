package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.CurrentlyPlayingPort
import de.chrgroth.quarkus.starters.StarterSkipPredicate
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@ApplicationScoped
@Suppress("Unused")
class CurrentlyPlayingFetchJob(
    private val currentlyPlaying: CurrentlyPlayingPort,
    private val scheduleState: CurrentlyPlayingScheduleState,
) {

    private val lastExecutedAtRef = AtomicReference<Instant>(Instant.EPOCH)

    @Scheduled(every = "10s", skipExecutionIf = StarterSkipPredicate::class)
    fun run() {
        val effectiveInterval = if (scheduleState.isPlaybackActive()) FAST_INTERVAL else SLOW_INTERVAL
        val now = Instant.now()
        val lastExecutedAt = lastExecutedAtRef.get()
        if (now.isBefore(lastExecutedAt.plus(effectiveInterval))) return
        if (!lastExecutedAtRef.compareAndSet(lastExecutedAt, now)) return
        currentlyPlaying.enqueueUpdates()
    }

    companion object {
        private val FAST_INTERVAL: Duration = Duration.ofSeconds(10)
        private val SLOW_INTERVAL: Duration = CurrentlyPlayingScheduleState.SLOW_INTERVAL
    }
}
