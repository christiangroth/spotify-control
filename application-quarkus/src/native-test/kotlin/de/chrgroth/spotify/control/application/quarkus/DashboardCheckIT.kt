package de.chrgroth.spotify.control.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

// Renders the real /dashboard page (native executable when invoked via `testNative`), so a Qute reflection gap
// that native-image analysis misses on untyped `Template.data()` bindings (see #886/#888) is caught in CI before
// merge instead of surfacing in production.
@QuarkusIntegrationTest
class DashboardCheckIT {

  @Test
  fun `dashboard page renders successfully for a fresh user`() {
    given()
      .cookie("spotify-session", SessionCookieForge.forgeSessionCookie("it-test-user"))
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="welcome-message""""))
      .body(containsString("""data-testid="histogram""""))
  }
}
