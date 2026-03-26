package de.chrgroth.spotify.control.adapter.out.scheduler

import de.chrgroth.spotify.control.domain.model.playback.PlaybackDetectedEvent
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackActivityPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@ApplicationScoped
class PlaybackActivityAdapter : PlaybackActivityPort {

    private val lastPlaybackDetectedAtRef = AtomicReference<Instant>(Instant.fromEpochMilliseconds(0))

    @Suppress("UnusedParameter")
    fun onPlaybackDetected(@Observes event: PlaybackDetectedEvent) {
        lastPlaybackDetectedAtRef.set(Clock.System.now())
    }

    override fun isPlaybackActive(): Boolean =
        (Clock.System.now() - lastPlaybackDetectedAtRef.get()) < PLAYBACK_ACTIVE_THRESHOLD

    override fun lastActivityTimestamp(): Instant? {
        val ts = lastPlaybackDetectedAtRef.get()
        return if (ts == Instant.fromEpochMilliseconds(0)) null else ts
    }

    companion object {
        private val PLAYBACK_ACTIVE_THRESHOLD = 5.minutes
    }
}
