package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.Test

@QuarkusTest
class LanguageSwitchTests {

  @Test
  fun `login page renders english text by default without a language cookie`() {
    given()
      .`when`()
      .get("/")
      .then()
      .statusCode(200)
      .body(containsString("""<html lang="en">"""))
      .body(containsString("Log in with Spotify"))
  }

  @Test
  fun `login page renders underscore pseudo locale when lang cookie is xx`() {
    given()
      .cookie("lang", "xx")
      .`when`()
      .get("/")
      .then()
      .statusCode(200)
      .body(containsString("""<html lang="xx">"""))
      .body(not(containsString("Log in with Spotify")))
      .body(containsString("____________")) // "Spotify" -> 7 non-whitespace chars -> 14 underscores
  }

  @Test
  fun `login page falls back to english for an unknown language cookie value`() {
    given()
      .cookie("lang", "de")
      .`when`()
      .get("/")
      .then()
      .statusCode(200)
      .body(containsString("""<html lang="en">"""))
      .body(containsString("Log in with Spotify"))
  }

  @Test
  fun `language toggle button is present with both language labels on the login page`() {
    given()
      .`when`()
      .get("/")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="language-toggle-button""""))
      .body(containsString("""class="language-toggle-label-en""""))
      .body(containsString("""class="language-toggle-label-xx""""))
  }
}

@QuarkusTest
@TestSecurity(user = "test-user-a")
class LanguageSwitchAuthenticatedTests {

  @Test
  fun `language toggle button is present and enabled on the dashboard`() {
    given()
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="language-toggle-button""""))
      .body(not(containsString("""data-testid="language-toggle-button" disabled""")))
  }

  @Test
  fun `dashboard renders underscore pseudo locale when lang cookie is xx`() {
    given()
      .cookie("lang", "xx")
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("""<html lang="xx">"""))
  }
}
