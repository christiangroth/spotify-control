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
      .body(containsString("Hi "))
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
  }

  @Test
  fun `dashboard page contains sse script`() {
    given()
      .`when`()
      .get("/ui/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("/ui/dashboard/events"))
  }
}
