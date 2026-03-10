package de.chrgroth.spotify.control.adapter.out.scheduler

import de.chrgroth.spotify.control.domain.port.out.PlaybackStatePort
import jakarta.enterprise.context.ApplicationScoped
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@ApplicationScoped
class CurrentlyPlayingScheduleState @JvmOverloads constructor(initialTime: Instant = Instant.EPOCH) : PlaybackStatePort {

    private val lastPlaybackDetectedAtRef = AtomicReference<Instant>(initialTime)

    val lastPlaybackDetectedAt: Instant
        get() = lastPlaybackDetectedAtRef.get()

    override fun onPlaybackDetected() {
        lastPlaybackDetectedAtRef.set(Instant.now())
    }

    fun isPlaybackActive(): Boolean =
        Duration.between(lastPlaybackDetectedAtRef.get(), Instant.now()) < SLOW_INTERVAL

    companion object {
        val SLOW_INTERVAL: Duration = Duration.ofMinutes(5)
    }
}
