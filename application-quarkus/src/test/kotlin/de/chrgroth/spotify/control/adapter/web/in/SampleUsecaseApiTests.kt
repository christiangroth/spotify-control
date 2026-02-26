package de.chrgroth.spotify.control.adapter.web.`in`

import de.chrgroth.spotify.control.adapter.`in`.web.SessionStore
import de.chrgroth.spotify.control.domain.model.UserId
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@QuarkusTest
class SampleUsecaseApiTests {

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
  fun `Say hello`() {
    RestAssured.given()
      .cookie("spotify-session", sessionId)
      .`when`()
      .get("/api/hello")
      .then()
      .statusCode(200)
      .body("message", CoreMatchers.`is`("Hello!"))
  }
}
