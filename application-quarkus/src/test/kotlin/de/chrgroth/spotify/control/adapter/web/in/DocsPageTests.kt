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
class DocsPageTests {

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
  fun `docs arc42 page is available and renders markdown content`() {
    given()
      .cookie("spotify-session", sessionId)
      .`when`()
      .get("/ui/docs/arc42")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("docs-rendered"))
      .body(containsString("Architecture Documentation"))
  }

  @Test
  fun `docs adr index page is available and lists adrs`() {
    given()
      .cookie("spotify-session", sessionId)
      .`when`()
      .get("/ui/docs/adr")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="adr-list""""))
      .body(containsString("Architecture Decision Records"))
  }

  @Test
  fun `docs adr detail page renders a specific adr`() {
    given()
      .cookie("spotify-session", sessionId)
      .`when`()
      .get("/ui/docs/adr/0001-using-arc42-as-project-documentation.md")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("docs-rendered"))
  }

  @Test
  fun `docs adr detail page returns not found for invalid filename`() {
    given()
      .cookie("spotify-session", sessionId)
      .`when`()
      .get("/ui/docs/adr/not-an-md-file.txt")
      .then()
      .statusCode(404)
  }

  @Test
  fun `docs releasenotes page is available and renders markdown content`() {
    given()
      .cookie("spotify-session", sessionId)
      .`when`()
      .get("/ui/docs/releasenotes")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("docs-rendered"))
      .body(containsString("Release Notes"))
  }
}
