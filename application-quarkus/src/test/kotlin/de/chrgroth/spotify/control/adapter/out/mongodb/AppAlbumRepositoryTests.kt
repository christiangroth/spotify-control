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
        albumType = "album",
        totalTracks = 10,
        albumTitle = "Album $suffix",
        imageLink = "https://img.example.com/cover-$suffix.jpg",
        releaseDate = "2023-01-01",
        releaseDatePrecision = "day",
        type = "album",
        artistId = "artist-$suffix",
        artistName = "Artist $suffix",
    )

    @Test
    fun `upsertAll persists new items and findByAlbumIds returns them`() {
        val item = album("new")
        appAlbumRepository.upsertAll(listOf(item))

        val result = appAlbumRepository.findByAlbumIds(setOf(item.albumId))

        assertThat(result).hasSize(1)
        assertThat(result[0].albumId).isEqualTo(item.albumId)
        assertThat(result[0].albumTitle).isEqualTo(item.albumTitle)
        assertThat(result[0].artistId).isEqualTo(item.artistId)
        assertThat(result[0].artistName).isEqualTo(item.artistName)
        assertThat(result[0].albumType).isEqualTo(item.albumType)
        assertThat(result[0].totalTracks).isEqualTo(item.totalTracks)
        assertThat(result[0].releaseDate).isEqualTo(item.releaseDate)
        assertThat(result[0].lastEnrichmentDate).isNotNull()
    }

    @Test
    fun `upsertAll overwrites existing fields on re-upsert`() {
        val albumId = "album-overwrite-${UUID.randomUUID()}"
        appAlbumRepository.upsertAll(listOf(AppAlbum(albumId = albumId, albumTitle = "Original Title")))

        appAlbumRepository.upsertAll(listOf(AppAlbum(albumId = albumId, albumTitle = "Updated Title")))

        val result = appAlbumRepository.findByAlbumIds(setOf(albumId))
        assertThat(result).hasSize(1)
        assertThat(result[0].albumTitle).isEqualTo("Updated Title")
    }

    @Test
    fun `upsertAll persists additional artist fields`() {
        val albumId = "album-multi-artist-${UUID.randomUUID()}"
        val item = AppAlbum(
            albumId = albumId,
            albumTitle = "Collab Album",
            artistId = "artist-1",
            artistName = "Artist One",
            additionalArtistIds = listOf("artist-2"),
            additionalArtistNames = listOf("Artist Two"),
        )
        appAlbumRepository.upsertAll(listOf(item))

        val result = appAlbumRepository.findByAlbumIds(setOf(albumId))
        assertThat(result).hasSize(1)
        assertThat(result[0].additionalArtistIds).containsExactly("artist-2")
        assertThat(result[0].additionalArtistNames).containsExactly("Artist Two")
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
