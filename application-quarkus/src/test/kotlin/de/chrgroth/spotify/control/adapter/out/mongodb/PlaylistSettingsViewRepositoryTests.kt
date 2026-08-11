package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.playlist.MissingArtist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSettingsEntry
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSettingsView
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.port.out.readmodel.PlaylistSettingsViewRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@QuarkusTest
class PlaylistSettingsViewRepositoryTests {

  @Inject
  lateinit var playlistSettingsViewRepository: PlaylistSettingsViewRepositoryPort

  @Inject
  lateinit var playlistSettingsViewDocumentRepository: PlaylistSettingsViewDocumentRepository

  @BeforeEach
  fun cleanUp() {
    playlistSettingsViewDocumentRepository.deleteAll()
  }

  private fun buildView() = PlaylistSettingsView(
    entries = listOf(
      PlaylistSettingsEntry(
        playlist = PlaylistInfo(
          spotifyPlaylistId = "playlist-1",
          snapshotId = "snap-1",
          lastSnapshotIdSyncTime = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
          name = "Playlist One",
          syncStatus = PlaylistSyncStatus.ACTIVE,
          type = PlaylistType.YEAR,
        ),
        numberOfTracks = 10,
        numberOfArtists = 5,
        numberOfMissingArtists = 1,
        missingArtists = listOf(MissingArtist(id = "artist-1", name = "Artist One")),
      ),
    ),
  )

  @Test
  fun `find returns null when no view has been saved yet`() {
    assertThat(playlistSettingsViewRepository.find()).isNull()
  }

  @Test
  fun `save and find round-trips the settings view`() {
    val view = buildView()

    playlistSettingsViewRepository.save(view)

    assertThat(playlistSettingsViewRepository.find()).isEqualTo(view)
  }

  @Test
  fun `save overwrites the previously stored settings view`() {
    playlistSettingsViewRepository.save(buildView())

    val updated = PlaylistSettingsView(entries = emptyList())
    playlistSettingsViewRepository.save(updated)

    assertThat(playlistSettingsViewRepository.find()).isEqualTo(updated)
  }
}
