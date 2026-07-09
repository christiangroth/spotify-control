package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.port.out.playback.CurrentlyPlayingRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@QuarkusTest
class CurrentlyPlayingRepositoryTests {

  @Inject
  lateinit var currentlyPlayingRepository: CurrentlyPlayingRepositoryPort

  private fun item(trackId: TrackId, observedAt: Instant) = CurrentlyPlayingItem(
    trackId = trackId,
    trackName = "Track",
    artistIds = emptyList(),
    artistNames = emptyList(),
    progressMs = 1000L,
    durationMs = 200000L,
    isPlaying = true,
    observedAt = observedAt,
    startTime = observedAt,
  )

  @Test
  fun `findMostRecentByTrack returns the item with the latest observedAt`() {
    val trackId = TrackId("track-1")
    val now = Instant.fromEpochSeconds(1_000_000)
    currentlyPlayingRepository.save(item(trackId, now))
    currentlyPlayingRepository.save(item(trackId, now + 30.seconds))
    currentlyPlayingRepository.save(item(trackId, now + 10.seconds))

    val result = currentlyPlayingRepository.findMostRecentByTrack(trackId)

    assertThat(result).isNotNull
    assertThat(result!!.observedAt).isEqualTo(now + 30.seconds)
  }

  @Test
  fun `findMostRecentByTrack returns null when no items match`() {
    val result = currentlyPlayingRepository.findMostRecentByTrack(TrackId("unknown-track"))
    assertThat(result).isNull()
  }
}
