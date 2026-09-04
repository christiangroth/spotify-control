package de.chrgroth.spotify.control.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

// Renders the real /stats page against the packaged artifact, exercising the StatsResource view classes
// (AggregationTab/AggregationView/RankEntryView/ActivityBarEntryView) and PlaybackAggregation Qute bindings -
// the same class of untyped `Template.data()` reflection gap fixed for /dashboard (#885/#888).
@QuarkusIntegrationTest
class StatsCheckIT {

  @Test
  fun `stats page renders successfully for a fresh user`() {
    given()
      .cookie("spotify-session", SessionCookieForge.forgeSessionCookie("it-test-stats-user"))
      .`when`()
      .get("/stats")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="stats-tabs""""))
  }
}
