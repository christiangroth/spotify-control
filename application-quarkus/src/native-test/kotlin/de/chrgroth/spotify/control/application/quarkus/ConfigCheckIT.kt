package de.chrgroth.spotify.control.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

// Renders the real /config page against the packaged artifact. ConfigurationStats/ConfigEntry/RuntimeConfig are
// always populated with real application config (not user data), so this reliably exercises their Qute bindings
// even for a fresh user - the same class of untyped `Template.data()` reflection gap fixed for /dashboard (#885/#888).
@QuarkusIntegrationTest
class ConfigCheckIT {

  @Test
  fun `config page renders successfully for a fresh user`() {
    given()
      .cookie("spotify-session", SessionCookieForge.forgeSessionCookie("it-test-config-user"))
      .`when`()
      .get("/config")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="default-throttle-interval""""))
      .body(containsString("""data-testid="env-table""""))
      .body(containsString("""data-testid="config-table""""))
  }
}
