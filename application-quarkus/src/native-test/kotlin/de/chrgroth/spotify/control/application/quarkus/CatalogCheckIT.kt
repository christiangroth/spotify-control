package de.chrgroth.spotify.control.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

// Renders the real /catalog page against the packaged artifact, exercising the ArtistBrowseItem/CatalogStats Qute
// bindings - the same class of untyped `Template.data()` reflection gap fixed for /dashboard (#885/#888).
@QuarkusIntegrationTest
class CatalogCheckIT {

  @Test
  fun `catalog page renders successfully for a fresh user`() {
    given()
      .cookie("spotify-session", SessionCookieForge.forgeSessionCookie("it-test-catalog-user"))
      .`when`()
      .get("/catalog")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""id="artist-list-container""""))
  }
}
