package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import java.util.UUID

@QuarkusTest
@TestSecurity(user = "test-user-a")
class SettingsPageTests {

  @Inject
  lateinit var playlistRepository: PlaylistRepositoryPort

  @Test
  fun `playlist settings page is available and displays playlists heading`() {
    given()
      .`when`()
      .get("/settings/playlist")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Playlists"))
  }

  @Test
  fun `playlist settings page displays artists column header`() {
    val now = Clock.System.now()
    playlistRepository.replaceAll(
      listOf(
        PlaylistInfo(
          spotifyPlaylistId = "playlist-${UUID.randomUUID()}",
          snapshotId = "snap-1",
          lastSnapshotIdSyncTime = now - 1.hours,
          name = "Playlist For Artists Column Test",
          syncStatus = PlaylistSyncStatus.ACTIVE,
        ),
      ),
    )

    given()
      .`when`()
      .get("/settings/playlist")
      .then()
      .statusCode(200)
      .body(containsString("Artists"))
  }

  @Test
  fun `playlist settings page displays sync now button`() {
    given()
      .`when`()
      .get("/settings/playlist")
      .then()
      .statusCode(200)
      .body(containsString("Sync Now"))
  }

  @Test
  fun `playback settings page is available and displays playback settings heading`() {
    given()
      .`when`()
      .get("/settings/playback")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Playback Settings"))
  }

  @Test
  fun `playback settings page displays recreate playback data button`() {
    given()
      .`when`()
      .get("/settings/playback")
      .then()
      .statusCode(200)
      .body(containsString("Recreate Playback Data"))
  }

  @Test
  fun `playlists settings page is available at new url and displays playlists heading`() {
    given()
      .`when`()
      .get("/playlists/settings")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Playlists"))
  }

  @Test
  fun `playlists checks page is available at new url and displays playlist checks heading`() {
    given()
      .`when`()
      .get("/playlists/checks")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Playlist Checks"))
  }

  @Test
  fun `playback page is available at new url and displays playback heading`() {
    given()
      .`when`()
      .get("/playback")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Playback"))
  }

  @Test
  fun `playback page displays playback event stats tiles`() {
    given()
      .`when`()
      .get("/playback")
      .then()
      .statusCode(200)
      .body(containsString("Playback Events (Last 30 Days)"))
      .body(containsString("Total Playback Events"))
  }

  @Test
  fun `playback page stats tiles link to playback events page without date by default`() {
    given()
      .`when`()
      .get("/playback")
      .then()
      .statusCode(200)
      .body(containsString("""href="/playback/events" """))
  }

  @Test
  fun `playback page stats tiles link to playback events page for requested date`() {
    given()
      .`when`()
      .get("/playback?date=2026-06-01")
      .then()
      .statusCode(200)
      .body(containsString("""href="/playback/events?date=2026-06-01" """))
  }

  @Test
  fun `playback page displays recreate playback data button`() {
    given()
      .`when`()
      .get("/playback")
      .then()
      .statusCode(200)
      .body(containsString("Recreate Playback Data"))
  }

  @Test
  fun `playback events page is available and displays heading`() {
    given()
      .`when`()
      .get("/playback/events")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Playback Events"))
  }

  @Test
  fun `stats page is available and displays stats heading`() {
    given()
      .`when`()
      .get("/stats")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Stats"))
      .body(containsString("""data-testid="stats-tabs""""))
      .body(containsString("Day"))
      .body(containsString("Week"))
      .body(containsString("Month"))
      .body(containsString("Quarter"))
      .body(containsString("Year"))
  }

  @Test
  fun `stats page displays aggregation detail sections`() {
    given()
      .`when`()
      .get("/stats")
      .then()
      .statusCode(200)
      .body(containsString("Top Artists"))
      .body(containsString("Top Albums"))
      .body(containsString("Top Tracks"))
      .body(containsString("""data-testid="stats-activity-section-week""""))
      .body(not(containsString("""data-testid="stats-activity-section-day"""")))
  }

}
