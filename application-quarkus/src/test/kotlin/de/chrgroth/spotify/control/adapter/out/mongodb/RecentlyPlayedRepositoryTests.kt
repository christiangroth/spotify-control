package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.RecentlyPlayedRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class RecentlyPlayedRepositoryTests {

    @Inject
    lateinit var recentlyPlayedRepository: RecentlyPlayedRepositoryPort

    private val userId = UserId("test-${UUID.randomUUID()}")
    private val now = Clock.System.now().let { Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) }

    private fun item(index: Int) = RecentlyPlayedItem(
        spotifyUserId = userId,
        trackId = "track-$index",
        trackName = "Track $index",
        artistIds = listOf("artist-id-$index"),
        artistNames = listOf("Artist $index"),
        playedAt = now - index.hours,
    )

    @Test
    fun `saveAll persists items and findExistingPlayedAts returns their playedAt values`() {
        val items = listOf(item(1), item(2))
        recentlyPlayedRepository.saveAll(items)

        val playedAts = items.map { it.playedAt }.toSet()
        val existing = recentlyPlayedRepository.findExistingPlayedAts(userId, playedAts)

        assertThat(existing).containsExactlyInAnyOrderElementsOf(playedAts)
    }

    @Test
    fun `findExistingPlayedAts returns empty set when no items exist`() {
        val playedAts = setOf(now - 10.hours)
        val existing = recentlyPlayedRepository.findExistingPlayedAts(userId, playedAts)
        assertThat(existing).isEmpty()
    }

    @Test
    fun `findExistingPlayedAts returns empty set for empty input`() {
        val existing = recentlyPlayedRepository.findExistingPlayedAts(userId, emptySet())
        assertThat(existing).isEmpty()
    }

    @Test
    fun `findExistingPlayedAts only returns playedAts that match`() {
        val savedItem = item(1)
        recentlyPlayedRepository.saveAll(listOf(savedItem))

        val newPlayedAt = now - 5.hours
        val result = recentlyPlayedRepository.findExistingPlayedAts(userId, setOf(savedItem.playedAt, newPlayedAt))

        assertThat(result).containsOnly(savedItem.playedAt)
        assertThat(result).doesNotContain(newPlayedAt)
    }
}
