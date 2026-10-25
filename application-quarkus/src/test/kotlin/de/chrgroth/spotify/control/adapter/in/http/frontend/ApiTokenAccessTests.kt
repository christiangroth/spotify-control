package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.model.user.ApiTokenScope
import de.chrgroth.spotify.control.domain.model.user.CreatedApiToken
import de.chrgroth.spotify.control.domain.port.`in`.user.ApiTokenPort
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class ApiTokenAccessTests {

  @Inject
  lateinit var apiTokens: ApiTokenPort

  private fun createToken(vararg scopes: ApiTokenScope): CreatedApiToken =
    apiTokens.createToken("test-${UUID.randomUUID()}", scopes.toSet()).getOrNull()!!

  @Test
  fun `token with read scope can read health and outbox`() {
    val token = createToken(ApiTokenScope.READ)

    given().auth().oauth2(token.plainToken)
      .`when`().get("/api/v1/health")
      .then().statusCode(200)
      .body("predicates", notNullValue())
      .body("mongoCollections", notNullValue())

    given().auth().oauth2(token.plainToken)
      .`when`().get("/api/v1/outbox")
      .then().statusCode(200)
  }

  @Test
  fun `token info endpoint reports name and scopes`() {
    val token = createToken(ApiTokenScope.READ, ApiTokenScope.WRITE)

    given().auth().oauth2(token.plainToken)
      .`when`().get("/api/v1/token")
      .then().statusCode(200)
      .body("name", equalTo("api-token:${token.token.name}"))
      .body("scopes", hasItem("READ"))
      .body("scopes", hasItem("WRITE"))
  }

  @Test
  fun `token without required scope is forbidden`() {
    val token = createToken(ApiTokenScope.WRITE)

    given().auth().oauth2(token.plainToken)
      .`when`().get("/api/v1/health")
      .then().statusCode(403)
  }

  @Test
  fun `token cannot reach endpoints outside the token api`() {
    val token = createToken(ApiTokenScope.READ, ApiTokenScope.SPOTIFY_READ, ApiTokenScope.WRITE)

    given().auth().oauth2(token.plainToken).redirects().follow(false)
      .`when`().get("/config")
      .then().statusCode(403)

    given().auth().oauth2(token.plainToken).redirects().follow(false)
      .`when`().post("/outbox-viewer/wipe")
      .then().statusCode(403)

    given().auth().oauth2(token.plainToken).redirects().follow(false)
      .contentType("application/json").body("""{"name":"escalate","scopes":["WRITE"]}""")
      .`when`().post("/settings/api-tokens")
      .then().statusCode(403)
  }

  @Test
  fun `dot segments below the api prefix are rejected`() {
    val token = createToken(ApiTokenScope.READ)

    given().auth().oauth2(token.plainToken).redirects().follow(false).urlEncodingEnabled(false)
      .`when`().get("/api/v1/%2E%2E/%2E%2E/config")
      .then().statusCode(403)
  }

  @Test
  fun `deactivated token is rejected and works again after reactivation`() {
    val token = createToken(ApiTokenScope.READ)
    apiTokens.setActive(token.token.id, false)

    given().auth().oauth2(token.plainToken)
      .`when`().get("/api/v1/health")
      .then().statusCode(401)

    apiTokens.setActive(token.token.id, true)

    given().auth().oauth2(token.plainToken)
      .`when`().get("/api/v1/health")
      .then().statusCode(200)
  }

  @Test
  fun `unknown token is rejected`() {
    given().auth().oauth2("spctl_unknown")
      .`when`().get("/api/v1/health")
      .then().statusCode(401)
  }

  @Test
  fun `token can read catalog, playback and playlist data`() {
    val token = createToken(ApiTokenScope.READ)

    listOf(
      "/api/v1/catalog/artists",
      "/api/v1/catalog/artists/shallow",
      "/api/v1/catalog/albums",
      "/api/v1/catalog/sync-timeline",
      "/api/v1/playback/events",
      "/api/v1/playlists/checks",
    ).forEach { path ->
      given().auth().oauth2(token.plainToken)
        .`when`().get(path)
        .then().statusCode(200)
    }
  }

  @Test
  fun `playback stats validates period and reports missing aggregations`() {
    val token = createToken(ApiTokenScope.READ)

    given().auth().oauth2(token.plainToken)
      .`when`().get("/api/v1/playback/stats?period=DECADE")
      .then().statusCode(400)

    given().auth().oauth2(token.plainToken)
      .`when`().get("/api/v1/playback/stats?period=YEAR&date=1990-06-15")
      .then().statusCode(404)
  }

  @Test
  fun `token with write scope can enqueue a playback poll`() {
    val token = createToken(ApiTokenScope.WRITE)

    given().auth().oauth2(token.plainToken)
      .`when`().post("/api/v1/playback/poll")
      .then().statusCode(200)
      .body("status", equalTo("ok"))
  }
}
