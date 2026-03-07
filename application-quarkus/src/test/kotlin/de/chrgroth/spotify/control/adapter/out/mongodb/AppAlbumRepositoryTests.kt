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

    private fun album(suffix: String) = AppAlbum(albumId = "album-$suffix-${UUID.randomUUID()}")

    @Test
    fun `upsertAll persists new items and findByAlbumIds returns them`() {
        val item = album("new")
        appAlbumRepository.upsertAll(listOf(item))

        val result = appAlbumRepository.findByAlbumIds(setOf(item.albumId))

        assertThat(result).hasSize(1)
        assertThat(result[0].albumId).isEqualTo(item.albumId)
    }

    @Test
    fun `upsertAll does not overwrite existing enriched fields`() {
        val albumId = "album-nooverwrite-${UUID.randomUUID()}"
        appAlbumRepository.upsertAll(listOf(AppAlbum(albumId = albumId)))
        appAlbumRepository.updateEnrichmentData(albumId, "Original Title", null, emptyList(), null)

        // Second upsertAll should not overwrite albumTitle already set by enrichment
        appAlbumRepository.upsertAll(listOf(AppAlbum(albumId = albumId)))

        val result = appAlbumRepository.findByAlbumIds(setOf(albumId))
        assertThat(result).hasSize(1)
        assertThat(result[0].albumTitle).isEqualTo("Original Title")
    }

    @Test
    fun `updateEnrichmentData updates all enrichment fields`() {
        val item = album("enrich")
        appAlbumRepository.upsertAll(listOf(item))

        appAlbumRepository.updateEnrichmentData(item.albumId, "Title", "https://img.example.com/cover.jpg", listOf("Rock"), "artist-1")

        val result = appAlbumRepository.findByAlbumIds(setOf(item.albumId))
        assertThat(result).hasSize(1)
        assertThat(result[0].albumTitle).isEqualTo("Title")
        assertThat(result[0].imageLink).isEqualTo("https://img.example.com/cover.jpg")
        assertThat(result[0].genres).containsExactly("Rock")
        assertThat(result[0].artistId).isEqualTo("artist-1")
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
