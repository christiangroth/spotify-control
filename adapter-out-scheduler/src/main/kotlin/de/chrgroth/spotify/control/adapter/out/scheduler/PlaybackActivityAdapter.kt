package de.chrgroth.spotify.control.adapter.out.scheduler

import de.chrgroth.spotify.control.domain.model.PlaybackDetectedEvent
import de.chrgroth.spotify.control.domain.port.out.PlaybackActivityPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@ApplicationScoped
class PlaybackActivityAdapter : PlaybackActivityPort {

    private val lastPlaybackDetectedAtRef = AtomicReference<Instant>(Instant.EPOCH)

    @Suppress("UnusedParameter")
    fun onPlaybackDetected(@Observes event: PlaybackDetectedEvent) {
        lastPlaybackDetectedAtRef.set(Instant.now())
    }

    override fun isPlaybackActive(): Boolean =
        Duration.between(lastPlaybackDetectedAtRef.get(), Instant.now()) < PLAYBACK_ACTIVE_THRESHOLD

    override fun lastActivityTimestamp(): Instant? {
        val ts = lastPlaybackDetectedAtRef.get()
        return if (ts == Instant.EPOCH) null else ts
    }

    companion object {
        private val PLAYBACK_ACTIVE_THRESHOLD: Duration = Duration.ofMinutes(5)
    }
}
