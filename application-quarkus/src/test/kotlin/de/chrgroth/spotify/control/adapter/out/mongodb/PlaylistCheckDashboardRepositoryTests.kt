package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckDashboardSummary
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckViolation
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistId
import de.chrgroth.spotify.control.domain.port.out.readmodel.PlaylistCheckDashboardRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@QuarkusTest
class PlaylistCheckDashboardRepositoryTests {

  @Inject
  lateinit var playlistCheckDashboardRepository: PlaylistCheckDashboardRepositoryPort

  @Inject
  lateinit var playlistCheckDashboardDocumentRepository: PlaylistCheckDashboardDocumentRepository

  @BeforeEach
  fun cleanUp() {
    playlistCheckDashboardDocumentRepository.deleteAll()
  }

  private fun buildSummary(lastCheck: Instant) = PlaylistCheckDashboardSummary(
    displayName = "Test User",
    checks = listOf(
      AppPlaylistCheck(
        checkId = "playlist-1:test-check",
        playlistId = PlaylistId("playlist-1"),
        lastCheck = lastCheck,
        succeeded = false,
        violations = listOf(PlaylistCheckViolation(id = "t1", message = "Artist – Track")),
      ),
    ),
    playlistNameById = mapOf("playlist-1" to "Playlist One"),
  )

  @Test
  fun `find returns null when no dashboard has been saved yet`() {
    assertThat(playlistCheckDashboardRepository.find()).isNull()
  }

  @Test
  fun `save and find round-trips the dashboard summary`() {
    val lastCheck = Clock.System.now().let { Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) }
    val summary = buildSummary(lastCheck)

    playlistCheckDashboardRepository.save(summary)

    assertThat(playlistCheckDashboardRepository.find()).isEqualTo(summary)
  }

  @Test
  fun `save overwrites the previously stored dashboard summary`() {
    val lastCheck = Clock.System.now().let { Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) }
    playlistCheckDashboardRepository.save(buildSummary(lastCheck))

    val updated = PlaylistCheckDashboardSummary(displayName = "Updated User", checks = emptyList(), playlistNameById = emptyMap())
    playlistCheckDashboardRepository.save(updated)

    assertThat(playlistCheckDashboardRepository.find()).isEqualTo(updated)
  }
}
