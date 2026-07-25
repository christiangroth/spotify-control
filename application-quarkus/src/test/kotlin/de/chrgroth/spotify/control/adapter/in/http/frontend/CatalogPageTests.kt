package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-a")
class CatalogPageTests {

  @Test
  fun `catalog page is available and displays wipe catalog button`() {
    given()
      .`when`()
      .get("/catalog")
      .then()
      .statusCode(200)
      .body(containsString("Wipe Catalog"))
      .body(containsString("wipe-catalog-btn"))
  }

  @Test
  fun `catalog wipe endpoint enqueues catalog wipe and returns ok status`() {
    given()
      .`when`()
      .post("/catalog/wipe")
      .then()
      .statusCode(200)
      .body("status", equalTo("ok"))
  }
}
