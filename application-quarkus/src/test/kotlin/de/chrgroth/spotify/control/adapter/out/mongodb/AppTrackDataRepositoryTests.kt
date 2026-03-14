package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.AlbumId
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.ArtistId
import de.chrgroth.spotify.control.domain.model.TrackId
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
        id = TrackId("track-$suffix-${UUID.randomUUID()}"),
        title = "Track $suffix",
        artistId = ArtistId("artist-$suffix"),
    )

    @Test
    fun `upsertAll persists new items and findByTrackIds returns them`() {
        val item = trackData("new")
        appTrackRepository.upsertAll(listOf(item))

        val result = appTrackRepository.findByTrackIds(setOf(item.id))

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo(item.id)
        assertThat(result[0].title).isEqualTo(item.title)
        assertThat(result[0].artistId).isEqualTo(item.artistId)
    }

    @Test
    fun `upsertAll updates existing items when id matches`() {
        val original = trackData("update")
        appTrackRepository.upsertAll(listOf(original))

        val updated = original.copy(title = "Updated Title")
        appTrackRepository.upsertAll(listOf(updated))

        val result = appTrackRepository.findByTrackIds(setOf(original.id))

        assertThat(result).hasSize(1)
        assertThat(result[0].title).isEqualTo("Updated Title")
    }

    @Test
    fun `findByTrackIds returns empty list for unknown trackIds`() {
        val result = appTrackRepository.findByTrackIds(setOf(TrackId("unknown-track-id-${UUID.randomUUID()}")))
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

        val result = appTrackRepository.findByTrackIds(setOf(item1.id, item2.id, item3.id))

        assertThat(result).hasSize(3)
        assertThat(result.map { it.id }).containsExactlyInAnyOrder(item1.id, item2.id, item3.id)
    }

    @Test
    fun `upsertAll sets albumId and albumName when provided`() {
        val item = trackData("album-set")
        appTrackRepository.upsertAll(listOf(item))

        val withAlbum = item.copy(albumId = AlbumId("album-x"), albumName = "Album X")
        appTrackRepository.upsertAll(listOf(withAlbum))

        val result = appTrackRepository.findByTrackIds(setOf(item.id))
        assertThat(result).hasSize(1)
        assertThat(result[0].albumId).isEqualTo(AlbumId("album-x"))
        assertThat(result[0].albumName).isEqualTo("Album X")
    }

    @Test
    fun `upsertAll does not overwrite existing albumId and albumName with null`() {
        val withAlbum = trackData("album-keep").copy(albumId = AlbumId("album-y"), albumName = "Album Y")
        appTrackRepository.upsertAll(listOf(withAlbum))

        val stubUpdate = withAlbum.copy(albumId = null, albumName = null)
        appTrackRepository.upsertAll(listOf(stubUpdate))

        val result = appTrackRepository.findByTrackIds(setOf(withAlbum.id))
        assertThat(result).hasSize(1)
        assertThat(result[0].albumId).isEqualTo(AlbumId("album-y"))
        assertThat(result[0].albumName).isEqualTo("Album Y")
    }

    @Test
    fun `updateTrackSyncData updates all sync fields`() {
        val item = trackData("sync")
        appTrackRepository.upsertAll(listOf(item))

        val synced = item.copy(
            albumId = AlbumId("album-1"),
            albumName = "Album One",
            artistName = "Artist Name",
            additionalArtistIds = listOf(ArtistId("artist-2")),
            additionalArtistNames = listOf("Artist Two"),
            discNumber = 1,
            durationMs = 210000,
            trackNumber = 5,
            type = "track",
        )
        appTrackRepository.updateTrackSyncData(synced)

        val result = appTrackRepository.findByTrackIds(setOf(item.id))
        assertThat(result).hasSize(1)
        assertThat(result[0].albumId).isEqualTo(AlbumId("album-1"))
        assertThat(result[0].albumName).isEqualTo("Album One")
        assertThat(result[0].artistName).isEqualTo("Artist Name")
        assertThat(result[0].additionalArtistIds).containsExactly(ArtistId("artist-2"))
        assertThat(result[0].additionalArtistNames).containsExactly("Artist Two")
        assertThat(result[0].discNumber).isEqualTo(1)
        assertThat(result[0].durationMs).isEqualTo(210000)
        assertThat(result[0].trackNumber).isEqualTo(5)
        assertThat(result[0].type).isEqualTo("track")
        assertThat(result[0].lastSync).isNotNull()
    }
}
