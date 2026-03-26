package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackPort
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class CurrentlyPlayingFetchJob(
    private val playback: PlaybackPort,
) {

    @Scheduled(every = "20s", skipExecutionIf = CurrentlyPlayingSkipPredicate::class)
    fun run() {
        playback.enqueueFetchCurrentlyPlaying()
    }
}
