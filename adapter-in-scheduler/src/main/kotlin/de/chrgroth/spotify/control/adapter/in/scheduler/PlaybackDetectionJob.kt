package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackPort
import io.micrometer.core.annotation.Timed
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class PlaybackDetectionJob(
  private val playback: PlaybackPort,
) {

  @Timed(value = "scheduler.job", extraTags = ["invoker", "PlaybackDetectionJob"])
  @Scheduled(every = "20s", skipExecutionIf = CurrentlyPlayingSkipPredicate::class)
  fun run() {
    playback.enqueueFetchPlaybackData()
  }

}
