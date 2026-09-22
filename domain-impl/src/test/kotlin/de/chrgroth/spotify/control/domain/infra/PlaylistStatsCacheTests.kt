package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistStats
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistCheckPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlaylistStatsCacheTests {

  private val playlistRepository: PlaylistRepositoryPort = mockk()
  private val playlistCheckPort: PlaylistCheckPort = mockk()
  private val cache = PlaylistStatsCache(playlistRepository, playlistCheckPort)

  private val now = Clock.System.now()

  @Test
  fun `current is zeroed out before the first refresh`() {
    assertThat(cache.current()).isEqualTo(PlaylistStats())
  }

  @Test
  fun `refresh populates current with the latest playlist counts`() {
    val outOfSync = PlaylistInfo(
      spotifyPlaylistId = "out-of-sync",
      snapshotId = "snap",
      lastSnapshotIdSyncTime = now,
      name = "Out of sync",
      syncStatus = PlaylistSyncStatus.ACTIVE,
      lastSyncTime = now - 1.days,
    )
    val inSync = PlaylistInfo(
      spotifyPlaylistId = "in-sync",
      snapshotId = "snap",
      lastSnapshotIdSyncTime = now,
      name = "In sync",
      syncStatus = PlaylistSyncStatus.ACTIVE,
      lastSyncTime = now,
    )
    val passive = PlaylistInfo(
      spotifyPlaylistId = "passive",
      snapshotId = "snap",
      lastSnapshotIdSyncTime = now,
      name = "Passive",
      syncStatus = PlaylistSyncStatus.PASSIVE,
    )
    every { playlistRepository.findAll() } returns listOf(outOfSync, inSync, passive)
    every { playlistCheckPort.pendingAlbumUpgradeCount() } returns 1

    cache.refresh()

    assertThat(cache.current()).isEqualTo(PlaylistStats(trackedCount = 2, outOfSyncCount = 1, pendingAlbumUpgradeCount = 1))
  }

  @Test
  fun `a failed refresh keeps the previously cached values instead of propagating`() {
    every { playlistRepository.findAll() } returns emptyList()
    every { playlistCheckPort.pendingAlbumUpgradeCount() } returns 0
    cache.refresh()

    every { playlistRepository.findAll() } throws IllegalStateException("mongo unreachable")
    cache.refresh()

    assertThat(cache.current()).isEqualTo(PlaylistStats())
  }

  @Test
  fun `current can be read repeatedly without re-querying the playlist repositories`() {
    every { playlistRepository.findAll() } returns emptyList()
    every { playlistCheckPort.pendingAlbumUpgradeCount() } returns 0
    cache.refresh()

    repeat(5) { cache.current() }

    verify(exactly = 1) { playlistRepository.findAll() }
  }
}
