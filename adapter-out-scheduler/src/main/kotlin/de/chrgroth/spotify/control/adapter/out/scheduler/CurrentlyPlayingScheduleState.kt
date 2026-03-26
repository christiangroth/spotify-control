package de.chrgroth.spotify.control.adapter.out.scheduler

import de.chrgroth.spotify.control.domain.model.playback.PlaybackDetectedEvent
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackStatePort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Event

@ApplicationScoped
class CurrentlyPlayingScheduleState(
    private val playbackDetectedEvent: Event<PlaybackDetectedEvent>,
) : PlaybackStatePort {

    override fun onPlaybackDetected() {
        playbackDetectedEvent.fire(PlaybackDetectedEvent())
    }
}
