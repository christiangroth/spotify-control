package de.chrgroth.spotify.control.adapter.web.`in`

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.junit.jupiter.api.Test

@QuarkusTest
class DocsPageTests {

  @Test
  fun `docs arc42 page requires authentication`() {
    given()
      .`when`()
      .get("/ui/docs/arc42")
      .then()
      .statusCode(401)
  }

  @Test
  fun `docs adr index page requires authentication`() {
    given()
      .`when`()
      .get("/ui/docs/adr")
      .then()
      .statusCode(401)
  }

  @Test
  fun `docs adr detail page requires authentication`() {
    given()
      .`when`()
      .get("/ui/docs/adr/0001-using-arc42-as-project-documentation.md")
      .then()
      .statusCode(401)
  }

  @Test
  fun `docs releasenotes page requires authentication`() {
    given()
      .`when`()
      .get("/ui/docs/releasenotes")
      .then()
      .statusCode(401)
  }
}
