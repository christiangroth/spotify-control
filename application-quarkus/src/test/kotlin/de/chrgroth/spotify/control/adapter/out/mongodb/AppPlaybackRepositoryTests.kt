package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.playback.AppPlaybackItem
import de.chrgroth.spotify.control.domain.port.out.playback.AppPlaybackRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@QuarkusTest
class AppPlaybackRepositoryTests {

  @Inject
  lateinit var appPlaybackRepository: AppPlaybackRepositoryPort

  private val now = Clock.System.now().let { Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) }

  private fun item(index: Int) = AppPlaybackItem(
    playedAt = now - index.hours,
    trackId = "track-$index",
    secondsPlayed = (index * 30).toLong(),
  )

  @BeforeEach
  fun cleanUp() {
    // single-user application: the collection is no longer scoped per user, so tests reset it explicitly for isolation
    appPlaybackRepository.deleteAll()
  }

  @Test
  fun `saveAll persists items and countAll returns correct count`() {
    appPlaybackRepository.saveAll(listOf(item(1), item(2), item(3)))

    val count = appPlaybackRepository.countAll()

    assertThat(count).isEqualTo(3)
  }

  @Test
  fun `findMostRecentPlayedAt returns most recent playedAt`() {
    appPlaybackRepository.saveAll(listOf(item(1), item(2), item(3)))

    val mostRecent = appPlaybackRepository.findMostRecentPlayedAt()

    assertThat(mostRecent).isEqualTo(item(1).playedAt)
  }

  @Test
  fun `findMostRecentPlayedAt returns null when no items exist`() {
    val result = appPlaybackRepository.findMostRecentPlayedAt()
    assertThat(result).isNull()
  }

  @Test
  fun `findExistingPlayedAts returns existing timestamps`() {
    val items = listOf(item(1), item(2))
    appPlaybackRepository.saveAll(items)

    val playedAts = items.map { it.playedAt }.toSet()
    val existing = appPlaybackRepository.findExistingPlayedAts(playedAts)

    assertThat(existing).containsExactlyInAnyOrderElementsOf(playedAts)
  }

  @Test
  fun `findExistingPlayedAts returns empty set for empty input`() {
    val existing = appPlaybackRepository.findExistingPlayedAts(emptySet())
    assertThat(existing).isEmpty()
  }

  @Test
  fun `deleteAllByTrackIds removes items for specified tracks`() {
    appPlaybackRepository.saveAll(listOf(item(1), item(2), item(3)))

    appPlaybackRepository.deleteAllByTrackIds(setOf("track-1", "track-2"))

    val existing = appPlaybackRepository.findExistingPlayedAts(
      setOf(item(1).playedAt, item(2).playedAt, item(3).playedAt),
    )
    assertThat(existing).containsOnly(item(3).playedAt)
  }

  @Test
  fun `deleteByPlayedAts removes only matching items`() {
    val item1 = item(1)
    val item2 = item(2)
    appPlaybackRepository.saveAll(listOf(item1, item2))

    appPlaybackRepository.deleteByPlayedAts(setOf(item1.playedAt))

    val remaining = appPlaybackRepository.findExistingPlayedAts(setOf(item1.playedAt, item2.playedAt))
    assertThat(remaining).containsOnly(item2.playedAt)
  }

  @Test
  fun `deleteByPlayedAts is a no-op for empty input`() {
    appPlaybackRepository.saveAll(listOf(item(1)))

    appPlaybackRepository.deleteByPlayedAts(emptySet())

    assertThat(appPlaybackRepository.countAll()).isEqualTo(1)
  }

  @Test
  fun `deleteAll removes all items`() {
    appPlaybackRepository.saveAll(listOf(item(1), item(2)))

    appPlaybackRepository.deleteAll()

    assertThat(appPlaybackRepository.countAll()).isZero()
  }

  @Test
  fun `findRecentlyPlayed returns limited results sorted by playedAt descending`() {
    appPlaybackRepository.saveAll(listOf(item(1), item(2), item(3)))

    val result = appPlaybackRepository.findRecentlyPlayed(2)

    assertThat(result.map { it.trackId }).containsExactly("track-1", "track-2")
  }

  @Test
  fun `sumSecondsPlayedByTrackIdSince aggregates seconds per track excluding zero values`() {
    val itemWithSeconds = AppPlaybackItem(playedAt = now - 1.hours, trackId = "track-a", secondsPlayed = 120L)
    val anotherItemSameTrack = AppPlaybackItem(playedAt = now - 2.hours, trackId = "track-a", secondsPlayed = 60L)
    val itemOtherTrack = AppPlaybackItem(playedAt = now - 3.hours, trackId = "track-b", secondsPlayed = 90L)
    val itemNoSeconds = AppPlaybackItem(playedAt = now - 4.hours, trackId = "track-c", secondsPlayed = 0L)
    appPlaybackRepository.saveAll(listOf(itemWithSeconds, anotherItemSameTrack, itemOtherTrack, itemNoSeconds))

    val since = now - 24.hours
    val result = appPlaybackRepository.sumSecondsPlayedByTrackIdSince(since)

    assertThat(result["track-a"]).isEqualTo(180L)
    assertThat(result["track-b"]).isEqualTo(90L)
    assertThat(result).doesNotContainKey("track-c")
  }

  @Test
  fun `findAllBetween returns only items within the given time window`() {
    val inside1 = AppPlaybackItem(playedAt = now - 2.hours, trackId = "inside-1", secondsPlayed = 60L)
    val inside2 = AppPlaybackItem(playedAt = now - 3.hours, trackId = "inside-2", secondsPlayed = 90L)
    val tooEarly = AppPlaybackItem(playedAt = now - 6.hours, trackId = "too-early", secondsPlayed = 30L)
    val tooLate = AppPlaybackItem(playedAt = now, trackId = "too-late", secondsPlayed = 30L)
    appPlaybackRepository.saveAll(listOf(inside1, inside2, tooEarly, tooLate))

    val from = now - 4.hours
    val to = now - 1.hours
    val result = appPlaybackRepository.findAllBetween(from, to)

    assertThat(result.map { it.trackId }).containsExactlyInAnyOrder("inside-1", "inside-2")
  }
}
