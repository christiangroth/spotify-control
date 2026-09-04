package de.chrgroth.spotify.control.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

// Renders the real /playlist-checks page against the packaged artifact, exercising the PlaylistCheckGroup/
// AppPlaylistCheck/PlaylistCheckViolation Qute bindings - the same class of untyped `Template.data()` reflection
// gap fixed for /dashboard (#885/#888).
@QuarkusIntegrationTest
class PlaylistChecksCheckIT {

  @Test
  fun `playlist checks page renders successfully for a fresh user`() {
    given()
      .cookie("spotify-session", SessionCookieForge.forgeSessionCookie("it-test-playlistchecks-user"))
      .`when`()
      .get("/playlist-checks")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="trigger-checks-btn""""))
  }
}
