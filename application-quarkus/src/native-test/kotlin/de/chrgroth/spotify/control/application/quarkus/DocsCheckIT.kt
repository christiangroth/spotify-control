package de.chrgroth.spotify.control.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

// Renders the real /docs/** pages (native executable when invoked via `testNative`) to catch a native-image resource
// bundling gap: the markdown files served here are plain classpath resources, not automatically included by
// native-image unless registered via `quarkus.native.resources.includes` (see #885).
@QuarkusIntegrationTest
class DocsCheckIT {

  @Test
  fun `arc42 doc page renders successfully`() {
    given()
      .cookie("spotify-session", SessionCookieForge.forgeSessionCookie("it-test-user"))
      .`when`()
      .get("/docs/arc42/arc42.md")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("docs-rendered"))
  }

  @Test
  fun `release notes page renders successfully`() {
    given()
      .cookie("spotify-session", SessionCookieForge.forgeSessionCookie("it-test-user"))
      .`when`()
      .get("/docs/releasenotes/RELEASENOTES.md")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
  }
}
