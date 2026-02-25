package de.chrgroth.spotify.control.adapter.web.`in`

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

@QuarkusTest
class LoginPageTests {

  @Inject
  @ConfigProperty(name = "app.build.version")
  lateinit var appBuildVersion: String

  @Test
  fun `login page is available and displays login button`() {
    given()
      .`when`()
      .get("/")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="login-button""""))
  }

  @Test
  fun `login page displays application version`() {
    given()
      .`when`()
      .get("/")
      .then()
      .statusCode(200)
      .body(containsString(appBuildVersion))
  }
}
