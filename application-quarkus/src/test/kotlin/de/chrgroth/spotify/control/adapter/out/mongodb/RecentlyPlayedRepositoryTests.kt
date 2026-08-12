package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPlayedRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@QuarkusTest
class RecentlyPlayedRepositoryTests {

  @Inject
  lateinit var recentlyPlayedRepository: RecentlyPlayedRepositoryPort

  @Inject
  lateinit var recentlyPlayedDocumentRepository: RecentlyPlayedDocumentRepository

  private val now = Clock.System.now().let { Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) }

  @BeforeEach
  fun cleanUp() {
    recentlyPlayedDocumentRepository.deleteAll()
  }

  private fun item(index: Int) = RecentlyPlayedItem(
    trackId = TrackId("track-$index"),
    trackName = "Track $index",
    artistIds = listOf(ArtistId("artist-id-$index")),
    artistNames = listOf("Artist $index"),
    playedAt = now - index.hours,
    durationSeconds = (index * 180).toLong(),
  )

  @Test
  fun `findMostRecentPlayedAt returns most recent playedAt`() {
    recentlyPlayedRepository.saveAll(listOf(item(1), item(2), item(3)))

    val mostRecent = recentlyPlayedRepository.findMostRecentPlayedAt()

    assertThat(mostRecent).isEqualTo(item(1).playedAt)
  }

  @Test
  fun `findMostRecentPlayedAt returns null when no items exist`() {
    val result = recentlyPlayedRepository.findMostRecentPlayedAt()
    assertThat(result).isNull()
  }

  @Test
  fun `saveAll persists items and findExistingPlayedAts returns their playedAt values`() {
    val items = listOf(item(1), item(2))
    recentlyPlayedRepository.saveAll(items)

    val playedAts = items.map { it.playedAt }.toSet()
    val existing = recentlyPlayedRepository.findExistingPlayedAts(playedAts)

    assertThat(existing).containsExactlyInAnyOrderElementsOf(playedAts)
  }

  @Test
  fun `findExistingPlayedAts returns empty set when no items exist`() {
    val playedAts = setOf(now - 10.hours)
    val existing = recentlyPlayedRepository.findExistingPlayedAts(playedAts)
    assertThat(existing).isEmpty()
  }

  @Test
  fun `findExistingPlayedAts returns empty set for empty input`() {
    val existing = recentlyPlayedRepository.findExistingPlayedAts(emptySet())
    assertThat(existing).isEmpty()
  }

  @Test
  fun `findExistingPlayedAts only returns playedAts that match`() {
    val savedItem = item(1)
    recentlyPlayedRepository.saveAll(listOf(savedItem))

    val newPlayedAt = now - 5.hours
    val result = recentlyPlayedRepository.findExistingPlayedAts(setOf(savedItem.playedAt, newPlayedAt))

    assertThat(result).containsOnly(savedItem.playedAt)
    assertThat(result).doesNotContain(newPlayedAt)
  }

  private fun nonTrackItem(index: Int) = RecentlyPlayedItem(
    trackId = TrackId("episode-$index"),
    trackName = "Episode $index",
    artistIds = emptyList(),
    artistNames = emptyList(),
    playedAt = now - index.hours,
  )

  @Test
  fun `deleteNonTracks removes items with empty artistIds`() {
    recentlyPlayedRepository.saveAll(listOf(item(1), nonTrackItem(2), nonTrackItem(3)))

    val deleted = recentlyPlayedRepository.deleteNonTracks()

    assertThat(deleted).isEqualTo(2L)
    val remaining = recentlyPlayedRepository.findExistingPlayedAts(setOf(item(1).playedAt, nonTrackItem(2).playedAt, nonTrackItem(3).playedAt))
    assertThat(remaining).containsOnly(item(1).playedAt)
  }

  @Test
  fun `deleteNonTracks returns zero when no non-track items exist`() {
    recentlyPlayedRepository.saveAll(listOf(item(4), item(5)))

    val deleted = recentlyPlayedRepository.deleteNonTracks()

    assertThat(deleted).isEqualTo(0L)
  }

  @Test
  fun `findSince persists and returns durationSeconds`() {
    val itemWithDuration = item(1)
    recentlyPlayedRepository.saveAll(listOf(itemWithDuration))

    val result = recentlyPlayedRepository.findSince(null)

    assertThat(result).hasSize(1)
    assertThat(result[0].durationSeconds).isEqualTo(itemWithDuration.durationSeconds)
  }

  @Test
  fun `findSince returns zero durationSeconds when not set`() {
    val itemWithoutDuration = RecentlyPlayedItem(
      trackId = TrackId("track-noduration"),
      trackName = "Track No Duration",
      artistIds = listOf(ArtistId("artist-id-1")),
      artistNames = listOf("Artist 1"),
      playedAt = now - 10.hours,
      durationSeconds = null,
    )
    recentlyPlayedRepository.saveAll(listOf(itemWithoutDuration))

    val result = recentlyPlayedRepository.findSince(null)

    val found = result.first { it.trackId == TrackId("track-noduration") }
    assertThat(found.durationSeconds).isEqualTo(0L)
  }

  @Test
  fun `findArtistNamesByIds returns names for artist ids found in playback history`() {
    recentlyPlayedRepository.saveAll(listOf(item(1), item(2)))

    val result = recentlyPlayedRepository.findArtistNamesByIds(setOf(ArtistId("artist-id-1"), ArtistId("artist-id-3")))

    assertThat(result).isEqualTo(mapOf(ArtistId("artist-id-1") to "Artist 1"))
  }

  @Test
  fun `findArtistNamesByIds returns empty map for empty input`() {
    val result = recentlyPlayedRepository.findArtistNamesByIds(emptySet())
    assertThat(result).isEmpty()
  }

  @Test
  fun `findArtistNamesByIds resolves names for multi-artist tracks`() {
    val multiArtistItem = RecentlyPlayedItem(
      trackId = TrackId("track-multi"),
      trackName = "Track Multi",
      artistIds = listOf(ArtistId("artist-main"), ArtistId("artist-feat")),
      artistNames = listOf("Main Artist", "Feat Artist"),
      playedAt = now - 20.hours,
    )
    recentlyPlayedRepository.saveAll(listOf(multiArtistItem))

    val result = recentlyPlayedRepository.findArtistNamesByIds(setOf(ArtistId("artist-feat")))

    assertThat(result).isEqualTo(mapOf(ArtistId("artist-feat") to "Feat Artist"))
  }
}
