package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-a")
class SingularityArtistsPageTests {

  @Test
  fun `singularity artists page is available`() {
    given()
      .`when`()
      .get("/playlists/singularity-artists")
      .then()
      .statusCode(200)
  }

  @Test
  fun `include endpoint returns not found for unknown artist`() {
    given()
      .`when`()
      .post("/playlists/singularity-artists/unknown-artist/include")
      .then()
      .statusCode(404)
  }

  @Test
  fun `exclude endpoint returns not found for unknown artist`() {
    given()
      .`when`()
      .post("/playlists/singularity-artists/unknown-artist/exclude")
      .then()
      .statusCode(404)
  }
}
