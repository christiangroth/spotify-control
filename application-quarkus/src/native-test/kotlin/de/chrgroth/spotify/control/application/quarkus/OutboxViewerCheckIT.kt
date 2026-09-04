package de.chrgroth.spotify.control.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

// Renders the real /outbox-viewer page against the packaged artifact, exercising the OutboxViewerPartition/OutboxTask
// Qute bindings - the same class of untyped `Template.data()` reflection gap fixed for /dashboard (#885/#888).
@QuarkusIntegrationTest
class OutboxViewerCheckIT {

  @Test
  fun `outbox viewer page renders successfully for a fresh user`() {
    given()
      .cookie("spotify-session", SessionCookieForge.forgeSessionCookie("it-test-outboxviewer-user"))
      .`when`()
      .get("/outbox-viewer")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""id="snippet-outbox-tasks""""))
  }
}
