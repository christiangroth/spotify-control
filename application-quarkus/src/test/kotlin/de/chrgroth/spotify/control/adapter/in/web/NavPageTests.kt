package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-a")
class NavPageTests {

  @Test
  fun `dashboard page displays cross-page navigation with dashboard tile first`() {
    given()
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="nav-tiles""""))
      .body(containsString("""data-testid="nav-tile-dashboard""""))
      .body(containsString("""data-testid="nav-tile-stats""""))
      .body(containsString("""data-testid="nav-tile-playlists""""))
      .body(containsString("""data-testid="nav-tile-checks""""))
      .body(containsString("""data-testid="nav-tile-catalog""""))
      .body(containsString("""data-testid="nav-tile-playback""""))
      .body(containsString("""href="/dashboard""""))
      .body(containsString("""href="/stats""""))
      .body(containsString("""href="/playlists/settings""""))
      .body(containsString("""href="/playlists/checks""""))
      .body(containsString("""href="/catalog""""))
      .body(containsString("""href="/playback""""))
  }

  @Test
  fun `cross-page navigation is shown on the tools config page`() {
    given()
      .`when`()
      .get("/config")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="nav-tiles""""))
      .body(containsString("""data-testid="nav-tile-dashboard""""))
  }

  @Test
  fun `cross-page navigation is shown on the health page`() {
    given()
      .`when`()
      .get("/health")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="nav-tiles""""))
  }

  @Test
  fun `cross-page navigation is shown on the release notes page`() {
    given()
      .`when`()
      .get("/docs/releasenotes/RELEASENOTES.md")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="nav-tiles""""))
  }

  @Test
  fun `cross-page navigation highlights the active tile via client-side pathname matching`() {
    given()
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("""[data-testid="nav-tiles"] a[href]"""))
      .body(containsString("nav-tile"))
  }

  @Test
  fun `cross-page navigation tiles show icon only on smallest breakpoint and icon with text on larger ones`() {
    given()
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .body(containsString("nav-tile-icon"))
      .body(containsString("d-inline-block d-sm-none d-md-inline-block"))
      .body(containsString("nav-tile-label d-none d-sm-inline"))
  }
}

@QuarkusTest
class NavPageUnauthenticatedTests {

  @Test
  fun `login page does not display cross-page navigation`() {
    given()
      .`when`()
      .get("/")
      .then()
      .statusCode(200)
      .body(not(containsString("""data-testid="nav-tiles"""")))
  }
}
