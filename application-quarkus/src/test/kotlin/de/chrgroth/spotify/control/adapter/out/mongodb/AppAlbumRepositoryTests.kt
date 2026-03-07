package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.AppAlbum
import de.chrgroth.spotify.control.domain.port.out.AppAlbumRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class AppAlbumRepositoryTests {

    @Inject
    lateinit var appAlbumRepository: AppAlbumRepositoryPort

    private fun album(suffix: String) = AppAlbum(
        albumId = "album-$suffix-${UUID.randomUUID()}",
        albumTitle = "Album $suffix",
        genres = listOf("Rock", "Pop"),
    )

    @Test
    fun `upsertAll persists new items and findByAlbumIds returns them`() {
        val item = album("new")
        appAlbumRepository.upsertAll(listOf(item))

        val result = appAlbumRepository.findByAlbumIds(setOf(item.albumId))

        assertThat(result).hasSize(1)
        assertThat(result[0].albumId).isEqualTo(item.albumId)
        assertThat(result[0].albumTitle).isEqualTo(item.albumTitle)
        assertThat(result[0].genres).containsExactlyElementsOf(item.genres)
    }

    @Test
    fun `upsertAll updates existing items when albumId matches`() {
        val original = album("update")
        appAlbumRepository.upsertAll(listOf(original))

        val updated = original.copy(albumTitle = "Updated Title")
        appAlbumRepository.upsertAll(listOf(updated))

        val result = appAlbumRepository.findByAlbumIds(setOf(original.albumId))

        assertThat(result).hasSize(1)
        assertThat(result[0].albumTitle).isEqualTo("Updated Title")
    }

    @Test
    fun `findByAlbumIds returns empty list for unknown albumIds`() {
        val result = appAlbumRepository.findByAlbumIds(setOf("unknown-album-${UUID.randomUUID()}"))
        assertThat(result).isEmpty()
    }

    @Test
    fun `findByAlbumIds returns empty list for empty input`() {
        val result = appAlbumRepository.findByAlbumIds(emptySet())
        assertThat(result).isEmpty()
    }
}
