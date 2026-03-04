package de.chrgroth.spotify.control.adapter.web.`in`

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-a")
class SettingsPageTests {

  @Test
  fun `settings page is available and displays playlists heading`() {
    given()
      .`when`()
      .get("/ui/settings")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Playlists"))
  }

  @Test
  fun `settings page displays sync now button`() {
    given()
      .`when`()
      .get("/ui/settings")
      .then()
      .statusCode(200)
      .body(containsString("Sync Now"))
  }
}
