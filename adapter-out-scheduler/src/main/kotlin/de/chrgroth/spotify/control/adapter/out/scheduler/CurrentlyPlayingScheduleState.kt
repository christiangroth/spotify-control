package de.chrgroth.spotify.control.adapter.out.scheduler

import de.chrgroth.spotify.control.domain.port.out.PlaybackStatePort
import jakarta.enterprise.context.ApplicationScoped
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@ApplicationScoped
class CurrentlyPlayingScheduleState : PlaybackStatePort {

    private val lastPlaybackDetectedAtRef = AtomicReference<Instant>(Instant.EPOCH)

    var lastPlaybackDetectedAt: Instant
        get() = lastPlaybackDetectedAtRef.get()
        internal set(value) { lastPlaybackDetectedAtRef.set(value) }

    override fun onPlaybackDetected() {
        lastPlaybackDetectedAtRef.set(Instant.now())
    }

    fun isPlaybackActive(): Boolean =
        Duration.between(lastPlaybackDetectedAtRef.get(), Instant.now()) < SLOW_INTERVAL

    companion object {
        val SLOW_INTERVAL: Duration = Duration.ofMinutes(5)
    }
}
