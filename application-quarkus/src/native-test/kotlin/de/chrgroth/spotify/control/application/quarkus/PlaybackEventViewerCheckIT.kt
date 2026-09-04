package de.chrgroth.spotify.control.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

// Renders the real /playback/events page against the packaged artifact, exercising the PlaybackEventViewerResult/
// PlaybackEventEntry Qute bindings - the same class of untyped `Template.data()` reflection gap fixed for
// /dashboard (#885/#888).
@QuarkusIntegrationTest
class PlaybackEventViewerCheckIT {

  @Test
  fun `playback event viewer page renders successfully for a fresh user`() {
    given()
      .cookie("spotify-session", SessionCookieForge.forgeSessionCookie("it-test-playbackevents-user"))
      .`when`()
      .get("/playback/events")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""id="status-banner""""))
  }
}
