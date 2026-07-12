package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistSyncStatus
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
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
    id = ArtistId("artist-$suffix-${UUID.randomUUID()}"),
    artistName = "Artist $suffix",
    lastSync = kotlin.time.Instant.fromEpochSeconds(1),
  )

  @Test
  fun `upsertAll persists new items and findByArtistIds returns them`() {
    val item = artist("new")
    appArtistRepository.upsertAll(listOf(item))

    val result = appArtistRepository.findByArtistIds(setOf(item.id))

    assertThat(result).hasSize(1)
    assertThat(result[0].id).isEqualTo(item.id)
    assertThat(result[0].artistName).isEqualTo(item.artistName)
  }

  @Test
  fun `upsertAll updates existing items when artistId matches`() {
    val original = artist("update")
    appArtistRepository.upsertAll(listOf(original))

    val updated = original.copy(artistName = "Updated Name")
    appArtistRepository.upsertAll(listOf(updated))

    val result = appArtistRepository.findByArtistIds(setOf(original.id))

    assertThat(result).hasSize(1)
    assertThat(result[0].artistName).isEqualTo("Updated Name")
  }

  @Test
  fun `findByArtistIds returns empty list for unknown artistIds`() {
    val result = appArtistRepository.findByArtistIds(setOf(ArtistId("unknown-artist-${UUID.randomUUID()}")))
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

    val result = appArtistRepository.findByArtistIds(setOf(item1.id, item2.id, item3.id))

    assertThat(result).hasSize(3)
    assertThat(result.map { it.id }).containsExactlyInAnyOrder(item1.id, item2.id, item3.id)
  }

  @Test
  fun `upsertAll stores all sync fields`() {
    val item = artist("sync").copy(
      imageLink = "https://example.com/image.jpg",
      type = "artist",
    )
    appArtistRepository.upsertAll(listOf(item))

    val result = appArtistRepository.findByArtistIds(setOf(item.id))
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

    assertThat(result.map { it.id }).contains(withImageAndBlankName.id)
    assertThat(result.map { it.id }).doesNotContain(withImageAndName.id, withoutImage.id)
  }

  @Test
  fun `setSyncStatus sets an artist to SHALLOW`() {
    val item = artist("shallow")
    appArtistRepository.upsertAll(listOf(item))

    appArtistRepository.setSyncStatus(item.id, ArtistSyncStatus.SHALLOW)

    val result = appArtistRepository.findByArtistIds(setOf(item.id))
    assertThat(result).hasSize(1)
    assertThat(result[0].syncStatus).isEqualTo(ArtistSyncStatus.SHALLOW)
  }

  @Test
  fun `setSyncStatus sets an artist back to SYNC`() {
    val item = artist("sync-again")
    appArtistRepository.upsertAll(listOf(item))
    appArtistRepository.setSyncStatus(item.id, ArtistSyncStatus.SHALLOW)

    appArtistRepository.setSyncStatus(item.id, ArtistSyncStatus.SYNC)

    val result = appArtistRepository.findByArtistIds(setOf(item.id))
    assertThat(result).hasSize(1)
    assertThat(result[0].syncStatus).isEqualTo(ArtistSyncStatus.SYNC)
  }

  @Test
  fun `upsertAll does not reset syncStatus`() {
    val item = artist("preserve-status")
    appArtistRepository.upsertAll(listOf(item))
    appArtistRepository.setSyncStatus(item.id, ArtistSyncStatus.SHALLOW)

    val updated = item.copy(artistName = "Updated Name")
    appArtistRepository.upsertAll(listOf(updated))

    val result = appArtistRepository.findByArtistIds(setOf(item.id))
    assertThat(result).hasSize(1)
    assertThat(result[0].artistName).isEqualTo("Updated Name")
    assertThat(result[0].syncStatus).isEqualTo(ArtistSyncStatus.SHALLOW)
  }

  @Test
  fun `new artist inserted via upsertAll keeps its given syncStatus`() {
    val item = artist("default-status").copy(syncStatus = ArtistSyncStatus.SHALLOW_ASSUMPTION)
    appArtistRepository.upsertAll(listOf(item))

    val result = appArtistRepository.findByArtistIds(setOf(item.id))
    assertThat(result).hasSize(1)
    assertThat(result[0].syncStatus).isEqualTo(ArtistSyncStatus.SHALLOW_ASSUMPTION)
  }

  @Test
  fun `findRecentlySynced returns artists ordered by lastSync descending`() {
    val older = artist("recent-older")
    appArtistRepository.upsertAll(listOf(older))
    Thread.sleep(5)
    val newer = artist("recent-newer")
    appArtistRepository.upsertAll(listOf(newer))

    val ids = appArtistRepository.findRecentlySynced(offset = 0, limit = 10000).map { it.id }

    assertThat(ids.indexOf(newer.id)).isLessThan(ids.indexOf(older.id))
  }

  @Test
  fun `findRecentlySynced honors offset and limit`() {
    val result = appArtistRepository.findRecentlySynced(offset = 0, limit = 1)
    assertThat(result).hasSizeLessThanOrEqualTo(1)
  }

  @Test
  fun `searchByName matches case-insensitively by substring`() {
    val suffix = UUID.randomUUID().toString()
    appArtistRepository.upsertAll(listOf(artist("Search-$suffix").copy(artistName = "The Search Artist $suffix")))

    val result = appArtistRepository.searchByName("search artist $suffix".uppercase(), 10)

    assertThat(result.map { it.artistName }).containsExactly("The Search Artist $suffix")
  }

  @Test
  fun `searchByName returns empty list when nothing matches`() {
    val result = appArtistRepository.searchByName("no-such-artist-${UUID.randomUUID()}", 10)
    assertThat(result).isEmpty()
  }

  @Test
  fun `searchByName honors limit`() {
    val suffix = UUID.randomUUID().toString()
    appArtistRepository.upsertAll(
      listOf(
        artist("limit1-$suffix").copy(artistName = "Limit Artist One $suffix"),
        artist("limit2-$suffix").copy(artistName = "Limit Artist Two $suffix"),
      ),
    )

    val result = appArtistRepository.searchByName("Limit Artist", 1)

    assertThat(result).hasSize(1)
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
  fun `findByStatuses returns only artists matching the given statuses`() {
    val syncAssumption = artist("status-sync-assumption").copy(syncStatus = ArtistSyncStatus.SYNC_ASSUMPTION)
    val shallowAssumption = artist("status-shallow-assumption").copy(syncStatus = ArtistSyncStatus.SHALLOW_ASSUMPTION)
    val sync = artist("status-sync").copy(syncStatus = ArtistSyncStatus.SYNC)
    appArtistRepository.upsertAll(listOf(syncAssumption, shallowAssumption, sync))

    val result = appArtistRepository.findByStatuses(setOf(ArtistSyncStatus.SYNC_ASSUMPTION, ArtistSyncStatus.SHALLOW_ASSUMPTION), 10000)

    assertThat(result.map { it.id }).contains(syncAssumption.id, shallowAssumption.id)
    assertThat(result.map { it.id }).doesNotContain(sync.id)
  }

  @Test
  fun `findByStatuses honors limit`() {
    val suffix = UUID.randomUUID().toString()
    appArtistRepository.upsertAll(
      listOf(
        artist("status-limit1-$suffix").copy(syncStatus = ArtistSyncStatus.SYNC_ASSUMPTION),
        artist("status-limit2-$suffix").copy(syncStatus = ArtistSyncStatus.SYNC_ASSUMPTION),
      ),
    )

    val result = appArtistRepository.findByStatuses(setOf(ArtistSyncStatus.SYNC_ASSUMPTION), 1)

    assertThat(result).hasSize(1)
  }

  @Test
  fun `countByStatuses counts only artists matching the given statuses`() {
    val before = appArtistRepository.countByStatuses(setOf(ArtistSyncStatus.SYNC_ASSUMPTION))
    val syncAssumption = artist("count-sync-assumption").copy(syncStatus = ArtistSyncStatus.SYNC_ASSUMPTION)
    val sync = artist("count-sync").copy(syncStatus = ArtistSyncStatus.SYNC)
    appArtistRepository.upsertAll(listOf(syncAssumption, sync))

    val after = appArtistRepository.countByStatuses(setOf(ArtistSyncStatus.SYNC_ASSUMPTION))

    assertThat(after).isEqualTo(before + 1)
  }
}
