package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.AppTrackData
import de.chrgroth.spotify.control.domain.port.out.AppTrackDataRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class AppTrackDataRepositoryTests {

    @Inject
    lateinit var appTrackDataRepository: AppTrackDataRepositoryPort

    private fun trackData(suffix: String) = AppTrackData(
        trackId = "track-$suffix-${UUID.randomUUID()}",
        artistIds = listOf("artist-$suffix"),
        trackTitle = "Track $suffix",
        artistNames = listOf("Artist $suffix"),
    )

    @Test
    fun `upsertAll persists new items and findByTrackIds returns them`() {
        val item = trackData("new")
        appTrackDataRepository.upsertAll(listOf(item))

        val result = appTrackDataRepository.findByTrackIds(setOf(item.trackId))

        assertThat(result).hasSize(1)
        assertThat(result[0].trackId).isEqualTo(item.trackId)
        assertThat(result[0].trackTitle).isEqualTo(item.trackTitle)
        assertThat(result[0].artistNames).containsExactlyElementsOf(item.artistNames)
    }

    @Test
    fun `upsertAll updates existing items when trackId matches`() {
        val original = trackData("update")
        appTrackDataRepository.upsertAll(listOf(original))

        val updated = original.copy(trackTitle = "Updated Title")
        appTrackDataRepository.upsertAll(listOf(updated))

        val result = appTrackDataRepository.findByTrackIds(setOf(original.trackId))

        assertThat(result).hasSize(1)
        assertThat(result[0].trackTitle).isEqualTo("Updated Title")
    }

    @Test
    fun `findByTrackIds returns empty list for unknown trackIds`() {
        val result = appTrackDataRepository.findByTrackIds(setOf("unknown-track-id-${UUID.randomUUID()}"))
        assertThat(result).isEmpty()
    }

    @Test
    fun `findByTrackIds returns empty list for empty input`() {
        val result = appTrackDataRepository.findByTrackIds(emptySet())
        assertThat(result).isEmpty()
    }
}
