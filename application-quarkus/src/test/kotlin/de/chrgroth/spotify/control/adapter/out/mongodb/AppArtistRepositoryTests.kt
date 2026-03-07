package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class AppArtistRepositoryTests {

    @Inject
    lateinit var appArtistRepository: AppArtistRepositoryPort

    private fun artist(suffix: String) = AppArtist(
        artistId = "artist-$suffix-${UUID.randomUUID()}",
        artistName = "Artist $suffix",
    )

    @Test
    fun `upsertAll persists new items and findByArtistIds returns them`() {
        val item = artist("new")
        appArtistRepository.upsertAll(listOf(item))

        val result = appArtistRepository.findByArtistIds(setOf(item.artistId))

        assertThat(result).hasSize(1)
        assertThat(result[0].artistId).isEqualTo(item.artistId)
        assertThat(result[0].artistName).isEqualTo(item.artistName)
    }

    @Test
    fun `upsertAll updates existing items when artistId matches`() {
        val original = artist("update")
        appArtistRepository.upsertAll(listOf(original))

        val updated = original.copy(artistName = "Updated Name")
        appArtistRepository.upsertAll(listOf(updated))

        val result = appArtistRepository.findByArtistIds(setOf(original.artistId))

        assertThat(result).hasSize(1)
        assertThat(result[0].artistName).isEqualTo("Updated Name")
    }

    @Test
    fun `findByArtistIds returns empty list for unknown artistIds`() {
        val result = appArtistRepository.findByArtistIds(setOf("unknown-artist-${UUID.randomUUID()}"))
        assertThat(result).isEmpty()
    }

    @Test
    fun `findByArtistIds returns empty list for empty input`() {
        val result = appArtistRepository.findByArtistIds(emptySet())
        assertThat(result).isEmpty()
    }
}
