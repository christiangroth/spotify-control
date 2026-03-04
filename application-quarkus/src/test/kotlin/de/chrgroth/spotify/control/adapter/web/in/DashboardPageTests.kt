package de.chrgroth.spotify.control.adapter.web.`in`

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-a")
class DashboardPageTests {

  @Test
  fun `dashboard page is available and displays logout link and personalized welcome message`() {
    given()
      .`when`()
      .get("/ui/dashboard")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="logout-link""""))
      .body(containsString("""data-testid="welcome-message""""))
      .body(containsString("Hej "))
  }

  @Test
  fun `dashboard page displays stats section`() {
    given()
      .`when`()
      .get("/ui/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("""id="stats-section""""))
      .body(containsString("Total Playback Events"))
      .body(containsString("Playback Events (Last 30 Days)"))
      .body(containsString("""data-testid="histogram""""))
  }

  @Test
  fun `dashboard page contains sse connection setup with reconnect interval`() {
    given()
      .`when`()
      .get("/ui/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("EventSource"))
      .body(containsString("/ui/dashboard/events"))
      .body(containsString("setInterval"))
      .body(containsString("60000"))
  }

  @Test
  fun `dashboard page uses specific sse events with fade updates instead of full reload`() {
    given()
      .`when`()
      .get("/ui/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("refresh-playback-data"))
      .body(containsString("refresh-playlist-metadata"))
      .body(containsString("fadeUpdate"))
  }

  @Test
  fun `dashboard snippet endpoint for playback data is available`() {
    given()
      .`when`()
      .get("/ui/dashboard/snippets/playback-data")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Total Playback Events"))
  }

  @Test
  fun `dashboard snippet endpoint for playlist metadata is available`() {
    given()
      .`when`()
      .get("/ui/dashboard/snippets/playlist-metadata")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Playlists synced"))
  }
}
