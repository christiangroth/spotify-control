package de.chrgroth.spotify.control.adapter.web.`in`

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

@QuarkusTest
class DashboardPageTests {

  @Test
  fun `dashboard page is available and displays logout link and welcome message`() {
    given()
      .`when`()
      .get("/ui/dashboard")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="logout-link""""))
      .body(containsString("""data-testid="welcome-message""""))
      .body(containsString("Welcome to SpCtl"))
  }
}
