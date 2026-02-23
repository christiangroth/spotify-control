package de.chrgroth.spotify.control.adapter.web.`in`

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@QuarkusTest
class SampleUsecaseApiTests {

  @Test
  fun `Say hello`() {
    val response = RestAssured.given()
      .`when`()
      .get("/api/hello")
      .then()
      .statusCode(200)
      .extract().body().asString()
    assertThat(response).isEqualTo("Hello!")
  }
}
