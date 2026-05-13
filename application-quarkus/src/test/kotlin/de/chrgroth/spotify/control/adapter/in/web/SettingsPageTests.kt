package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-a")
class SettingsPageTests {

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
      .body(containsString("Activity by Day and Time Window"))
  }

}
