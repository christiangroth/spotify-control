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
        lastSync = kotlin.time.Instant.fromEpochSeconds(1),
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

    @Test
    fun `findByArtistIds returns all matching items in a single batch`() {
        val item1 = artist("batch1")
        val item2 = artist("batch2")
        val item3 = artist("batch3")
        appArtistRepository.upsertAll(listOf(item1, item2, item3))

        val result = appArtistRepository.findByArtistIds(setOf(item1.artistId, item2.artistId, item3.artistId))

        assertThat(result).hasSize(3)
        assertThat(result.map { it.artistId }).containsExactlyInAnyOrder(item1.artistId, item2.artistId, item3.artistId)
    }

    @Test
    fun `upsertAll stores all sync fields`() {
        val item = artist("sync").copy(
            imageLink = "https://example.com/image.jpg",
            type = "artist",
        )
        appArtistRepository.upsertAll(listOf(item))

        val result = appArtistRepository.findByArtistIds(setOf(item.artistId))
        assertThat(result).hasSize(1)
        assertThat(result[0].artistName).isEqualTo(item.artistName)
        assertThat(result[0].imageLink).isEqualTo("https://example.com/image.jpg")
        assertThat(result[0].type).isEqualTo("artist")
        assertThat(result[0].lastSync).isNotEqualTo(kotlin.time.Instant.DISTANT_PAST)
    }

    @Test
    fun `findWithImageLinkAndBlankName returns only artists with imageLink and blank artistName`() {
        val withImageAndBlankName = artist("blank-name").copy(artistName = "", imageLink = "https://img.example.com/1.jpg")
        val withImageAndName = artist("has-name").copy(imageLink = "https://img.example.com/2.jpg")
        val withoutImage = artist("no-image").copy(artistName = "")
        appArtistRepository.upsertAll(listOf(withImageAndBlankName, withImageAndName, withoutImage))
        val result = appArtistRepository.findWithImageLinkAndBlankName()

        assertThat(result.map { it.artistId }).contains(withImageAndBlankName.artistId)
        assertThat(result.map { it.artistId }).doesNotContain(withImageAndName.artistId, withoutImage.artistId)
    }

    @Test
    fun `countAll returns total number of artists`() {
        val before = appArtistRepository.countAll()
        val artist1 = artist("count1")
        val artist2 = artist("count2")
        appArtistRepository.upsertAll(listOf(artist1, artist2))

        val after = appArtistRepository.countAll()

        assertThat(after).isEqualTo(before + 2)
    }

    @Test
    fun `findByPlaybackProcessingStatusPaged returns artists sorted by name within given offset and limit`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val artistC = artist("paged-C-$suffix").copy(artistName = "C Artist $suffix")
        val artistA = artist("paged-A-$suffix").copy(artistName = "A Artist $suffix")
        val artistB = artist("paged-B-$suffix").copy(artistName = "B Artist $suffix")
        appArtistRepository.upsertAll(listOf(artistC, artistA, artistB))

        val totalUndecided = appArtistRepository.countByPlaybackProcessingStatus(
            de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus.UNDECIDED,
        )
        val allResults = appArtistRepository.findByPlaybackProcessingStatusPaged(
            de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus.UNDECIDED, 0, totalUndecided.toInt(),
        )

        val insertedIds = setOf(artistA.artistId, artistB.artistId, artistC.artistId)
        val found = allResults.filter { it.artistId in insertedIds }
        assertThat(found).hasSize(3)
        assertThat(found.map { it.artistName }).containsExactly(
            "A Artist $suffix",
            "B Artist $suffix",
            "C Artist $suffix",
        )
    }

    @Test
    fun `findByPlaybackProcessingStatusPaged returns empty list when offset exceeds available items`() {
        val result = appArtistRepository.findByPlaybackProcessingStatusPaged(
            de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus.UNDECIDED, 100000, 50,
        )
        assertThat(result).isEmpty()
    }

    @Test
    fun `countByPlaybackProcessingStatus returns correct count for status`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val artist1 = artist("cnt-$suffix-1")
        val artist2 = artist("cnt-$suffix-2")
        appArtistRepository.upsertAll(listOf(artist1, artist2))

        val before = appArtistRepository.countByPlaybackProcessingStatus(
            de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus.UNDECIDED,
        )
        appArtistRepository.updatePlaybackProcessingStatus(
            artist1.artistId,
            de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus.ACTIVE,
        )
        val afterUpdate = appArtistRepository.countByPlaybackProcessingStatus(
            de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus.UNDECIDED,
        )

        assertThat(afterUpdate).isEqualTo(before - 1)
        assertThat(appArtistRepository.countByPlaybackProcessingStatus(
            de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus.ACTIVE,
        )).isGreaterThanOrEqualTo(1)
    }
}
