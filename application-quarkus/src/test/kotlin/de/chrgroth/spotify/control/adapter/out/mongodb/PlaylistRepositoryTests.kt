package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class PlaylistRepositoryTests {

  @Inject
  lateinit var playlistRepository: PlaylistRepositoryPort

  private fun buildPlaylistInfo(id: String, syncStatus: PlaylistSyncStatus = PlaylistSyncStatus.PASSIVE): PlaylistInfo {
    val now = Clock.System.now().let { Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) }
    return PlaylistInfo(
      spotifyPlaylistId = id,
      snapshotId = "snap-1",
      lastSnapshotIdSyncTime = now - 1.hours,
      name = "Playlist $id",
      syncStatus = syncStatus,
    )
  }

  @Test
  fun `replaceAll with empty list removes all playlists`() {
    playlistRepository.replaceAll(emptyList())

    assertThat(playlistRepository.findAll()).isEmpty()
  }

  @Test
  fun `replaceAll and findAll round-trips playlists correctly`() {
    val p1 = "p1-${UUID.randomUUID()}"
    val p2 = "p2-${UUID.randomUUID()}"
    val playlists = listOf(
      buildPlaylistInfo(p1, PlaylistSyncStatus.ACTIVE),
      buildPlaylistInfo(p2, PlaylistSyncStatus.PASSIVE),
    )

    playlistRepository.replaceAll(playlists)

    val found = playlistRepository.findAll()
    assertThat(found).hasSize(2)
    val byId = found.associateBy { it.spotifyPlaylistId }
    assertThat(byId[p1]!!.syncStatus).isEqualTo(PlaylistSyncStatus.ACTIVE)
    assertThat(byId[p2]!!.syncStatus).isEqualTo(PlaylistSyncStatus.PASSIVE)
  }

  @Test
  fun `replaceAll replaces all previous playlists`() {
    playlistRepository.replaceAll(listOf(buildPlaylistInfo("p1-${UUID.randomUUID()}"), buildPlaylistInfo("p2-${UUID.randomUUID()}")))

    val p3 = "p3-${UUID.randomUUID()}"
    playlistRepository.replaceAll(listOf(buildPlaylistInfo(p3)))

    val found = playlistRepository.findAll()
    assertThat(found).hasSize(1)
    assertThat(found[0].spotifyPlaylistId).isEqualTo(p3)
  }

  @Test
  fun `updateLastSyncTime sets lastSyncTime on existing playlist metadata`() {
    val p1 = "p1-${UUID.randomUUID()}"
    playlistRepository.replaceAll(listOf(buildPlaylistInfo(p1)))
    val syncTime = Clock.System.now().let { Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) }

    playlistRepository.updateLastSyncTime(p1, syncTime)

    val found = playlistRepository.findAll()
    assertThat(found).hasSize(1)
    assertThat(found[0].lastSyncTime).isEqualTo(syncTime)
  }

  @Test
  fun `updateLastSyncTime is a no-op for unknown playlist`() {
    playlistRepository.replaceAll(emptyList())

    playlistRepository.updateLastSyncTime("unknown-${UUID.randomUUID()}", Clock.System.now())

    assertThat(playlistRepository.findAll()).isEmpty()
  }

  @Test
  fun `replaceAll and findAll round-trips lastSyncTime correctly`() {
    val syncTime = Clock.System.now().let { Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) }
    val playlist = buildPlaylistInfo("p1-${UUID.randomUUID()}").copy(lastSyncTime = syncTime)

    playlistRepository.replaceAll(listOf(playlist))

    val found = playlistRepository.findAll()
    assertThat(found).hasSize(1)
    assertThat(found[0].lastSyncTime).isEqualTo(syncTime)
  }
}
