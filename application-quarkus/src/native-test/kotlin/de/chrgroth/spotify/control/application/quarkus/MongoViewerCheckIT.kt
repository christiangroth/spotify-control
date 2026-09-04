package de.chrgroth.spotify.control.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

// Renders the real /mongodb-viewer page against the packaged artifact. MongoViewerResult/MongoViewerField are
// populated from the actual collection metadata (not user data), so this reliably exercises their Qute bindings
// even for a fresh user - the same class of untyped `Template.data()` reflection gap fixed for /dashboard (#885/#888).
@QuarkusIntegrationTest
class MongoViewerCheckIT {

  @Test
  fun `mongodb viewer page renders successfully for a fresh user`() {
    given()
      .cookie("spotify-session", SessionCookieForge.forgeSessionCookie("it-test-mongoviewer-user"))
      .`when`()
      .get("/mongodb-viewer")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""id="viewer-form""""))
      .body(containsString("""id="collection-select""""))
  }
}
