package de.chrgroth.spotify.control.adapter.web.`in`

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured
import org.hamcrest.CoreMatchers
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-a")
class SampleUsecaseApiTests {

  @Test
  fun `Say hello`() {
    RestAssured.given()
      .`when`()
      .get("/api/hello")
      .then()
      .statusCode(200)
      .body("message", CoreMatchers.`is`("Hello!"))
  }
}
