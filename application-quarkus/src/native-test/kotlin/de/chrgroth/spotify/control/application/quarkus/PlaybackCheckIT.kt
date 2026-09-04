package de.chrgroth.spotify.control.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

// Renders the real /playback page against the packaged artifact, exercising the DashboardStats Qute binding -
// the same class of untyped `Template.data()` reflection gap fixed for /dashboard (#885/#888).
@QuarkusIntegrationTest
class PlaybackCheckIT {

  @Test
  fun `playback page renders successfully for a fresh user`() {
    given()
      .cookie("spotify-session", SessionCookieForge.forgeSessionCookie("it-test-playback-user"))
      .`when`()
      .get("/playback")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""id="rebuild-playback-btn""""))
  }
}
