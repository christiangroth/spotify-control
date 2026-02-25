package de.chrgroth.spotify.control.adapter.web.`in`

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

@QuarkusTest
class DocsPageTests {

  @Test
  fun `docs arc42 page is available and renders markdown content`() {
    given()
      .`when`()
      .get("/ui/docs/arc42")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("docs-rendered"))
      .body(containsString("Architecture Documentation"))
  }

  @Test
  fun `docs adr index page is available and lists adrs`() {
    given()
      .`when`()
      .get("/ui/docs/adr")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="adr-list""""))
      .body(containsString("Architecture Decision Records"))
  }

  @Test
  fun `docs adr detail page renders a specific adr`() {
    given()
      .`when`()
      .get("/ui/docs/adr/0001-using-arc42-as-project-documentation.md")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("docs-rendered"))
  }

  @Test
  fun `docs adr detail page returns bad request for invalid filename`() {
    given()
      .`when`()
      .get("/ui/docs/adr/not-an-md-file.txt")
      .then()
      .statusCode(400)
  }

  @Test
  fun `docs releasenotes page is available and renders markdown content`() {
    given()
      .`when`()
      .get("/ui/docs/releasenotes")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("docs-rendered"))
      .body(containsString("Release Notes"))
  }
}
