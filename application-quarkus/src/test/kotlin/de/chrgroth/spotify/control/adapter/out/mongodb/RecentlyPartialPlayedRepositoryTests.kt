package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPartialPlayedItem
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPartialPlayedRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@QuarkusTest
class RecentlyPartialPlayedRepositoryTests {

  @Inject
  lateinit var recentlyPartialPlayedRepository: RecentlyPartialPlayedRepositoryPort

  private val now = Clock.System.now().let { Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) }

  private fun item(index: Int, trackSuffix: String = "$index") = RecentlyPartialPlayedItem(
    trackId = TrackId("track-$trackSuffix"),
    trackName = "Track $trackSuffix",
    artistIds = listOf(ArtistId("artist-id-$index")),
    artistNames = listOf("Artist $index"),
    playedAt = now - index.hours,
    startTime = now - index.hours - (index * 30).seconds,
    playedSeconds = (index * 30).toLong(),
  )

  @Test
  fun `findByTrackIds returns matching items`() {
    val item1 = item(1, "a")
    val item2 = item(2, "b")
    val item3 = item(3, "c")
    recentlyPartialPlayedRepository.saveAll(listOf(item1, item2, item3))

    val result = recentlyPartialPlayedRepository.findByTrackIds(
      setOf(TrackId("track-a"), TrackId("track-b")),
    )

    assertThat(result.map { it.trackId }).containsExactlyInAnyOrder(TrackId("track-a"), TrackId("track-b"))
  }

  @Test
  fun `findByTrackIds returns empty list for empty input`() {
    recentlyPartialPlayedRepository.saveAll(listOf(item(1)))

    val result = recentlyPartialPlayedRepository.findByTrackIds(emptySet())

    assertThat(result).isEmpty()
  }

  @Test
  fun `findByTrackIds returns empty list when no match`() {
    recentlyPartialPlayedRepository.saveAll(listOf(item(1, "x")))

    val result = recentlyPartialPlayedRepository.findByTrackIds(setOf(TrackId("track-unknown")))

    assertThat(result).isEmpty()
  }

  @Test
  fun `deleteByPlayedAts removes only matching items`() {
    val item1 = item(1, "del-a")
    val item2 = item(2, "del-b")
    val item3 = item(3, "del-c")
    recentlyPartialPlayedRepository.saveAll(listOf(item1, item2, item3))

    recentlyPartialPlayedRepository.deleteByPlayedAts(setOf(item1.playedAt, item2.playedAt))

    val remaining = recentlyPartialPlayedRepository.findExistingPlayedAts(
      setOf(item1.playedAt, item2.playedAt, item3.playedAt),
    )
    assertThat(remaining).containsOnly(item3.playedAt)
  }

  @Test
  fun `deleteByPlayedAts is a no-op for empty input`() {
    val item1 = item(1, "noop-a")
    recentlyPartialPlayedRepository.saveAll(listOf(item1))

    recentlyPartialPlayedRepository.deleteByPlayedAts(emptySet())

    val remaining = recentlyPartialPlayedRepository.findExistingPlayedAts(setOf(item1.playedAt))
    assertThat(remaining).containsOnly(item1.playedAt)
  }
}
