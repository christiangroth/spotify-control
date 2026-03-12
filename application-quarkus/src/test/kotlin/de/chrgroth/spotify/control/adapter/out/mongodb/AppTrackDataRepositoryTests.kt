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

    @Test
    fun `findByTrackIds returns all matching items in a single batch`() {
        val item1 = trackData("batch1")
        val item2 = trackData("batch2")
        val item3 = trackData("batch3")
        appTrackRepository.upsertAll(listOf(item1, item2, item3))

        val result = appTrackRepository.findByTrackIds(setOf(item1.trackId, item2.trackId, item3.trackId))

        assertThat(result).hasSize(3)
        assertThat(result.map { it.trackId }).containsExactlyInAnyOrder(item1.trackId, item2.trackId, item3.trackId)
    }

    @Test
    fun `updateTrackEnrichmentData updates all enrichment fields`() {
        val item = trackData("enrich")
        appTrackRepository.upsertAll(listOf(item))

        val enriched = item.copy(
            albumId = "album-1",
            albumName = "Album One",
            artistName = "Artist Name",
            additionalArtistIds = listOf("artist-2"),
            additionalArtistNames = listOf("Artist Two"),
            discNumber = 1,
            durationMs = 210000,
            trackNumber = 5,
            type = "track",
        )
        appTrackRepository.updateTrackEnrichmentData(enriched)

        val result = appTrackRepository.findByTrackIds(setOf(item.trackId))
        assertThat(result).hasSize(1)
        assertThat(result[0].albumId).isEqualTo("album-1")
        assertThat(result[0].albumName).isEqualTo("Album One")
        assertThat(result[0].artistName).isEqualTo("Artist Name")
        assertThat(result[0].additionalArtistIds).containsExactly("artist-2")
        assertThat(result[0].additionalArtistNames).containsExactly("Artist Two")
        assertThat(result[0].discNumber).isEqualTo(1)
        assertThat(result[0].durationMs).isEqualTo(210000)
        assertThat(result[0].trackNumber).isEqualTo(5)
        assertThat(result[0].type).isEqualTo("track")
        assertThat(result[0].lastEnrichmentDate).isNotNull()
    }
}
