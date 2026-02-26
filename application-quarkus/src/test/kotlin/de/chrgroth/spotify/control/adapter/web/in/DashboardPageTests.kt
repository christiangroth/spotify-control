package de.chrgroth.spotify.control.adapter.web.`in`

import de.chrgroth.spotify.control.adapter.`in`.web.SessionStore
import de.chrgroth.spotify.control.domain.model.UserId
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@QuarkusTest
class DashboardPageTests {

  @Inject
  private lateinit var sessionStore: SessionStore

  private lateinit var sessionId: String

  @BeforeEach
  fun setUp() {
    sessionId = sessionStore.createSession(UserId("test-user-a"))
  }

  @AfterEach
  fun tearDown() {
    sessionStore.removeSession(sessionId)
  }

  @Test
  fun `dashboard page is available and displays logout link and welcome message`() {
    given()
      .cookie("spotify-session", sessionId)
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
