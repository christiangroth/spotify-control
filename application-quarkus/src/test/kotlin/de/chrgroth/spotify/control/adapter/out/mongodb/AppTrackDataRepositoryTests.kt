package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class AppTrackDataRepositoryTests {

    @Inject
    lateinit var appTrackRepository: AppTrackRepositoryPort

    private fun trackData(suffix: String) = AppTrack(
        trackId = "track-$suffix-${UUID.randomUUID()}",
        trackTitle = "Track $suffix",
        artistId = "artist-$suffix",
    )

    @Test
    fun `upsertAll persists new items and findByTrackIds returns them`() {
        val item = trackData("new")
        appTrackRepository.upsertAll(listOf(item))

        val result = appTrackRepository.findByTrackIds(setOf(item.trackId))

        assertThat(result).hasSize(1)
        assertThat(result[0].trackId).isEqualTo(item.trackId)
        assertThat(result[0].trackTitle).isEqualTo(item.trackTitle)
        assertThat(result[0].artistId).isEqualTo(item.artistId)
    }

    @Test
    fun `upsertAll updates existing items when trackId matches`() {
        val original = trackData("update")
        appTrackRepository.upsertAll(listOf(original))

        val updated = original.copy(trackTitle = "Updated Title")
        appTrackRepository.upsertAll(listOf(updated))

        val result = appTrackRepository.findByTrackIds(setOf(original.trackId))

        assertThat(result).hasSize(1)
        assertThat(result[0].trackTitle).isEqualTo("Updated Title")
    }

    @Test
    fun `findByTrackIds returns empty list for unknown trackIds`() {
        val result = appTrackRepository.findByTrackIds(setOf("unknown-track-id-${UUID.randomUUID()}"))
        assertThat(result).isEmpty()
    }

    @Test
    fun `findByTrackIds returns empty list for empty input`() {
        val result = appTrackRepository.findByTrackIds(emptySet())
        assertThat(result).isEmpty()
    }
}
