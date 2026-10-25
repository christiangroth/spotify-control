package de.chrgroth.spotify.control.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.jupiter.api.Test

// Exercises the full API token round trip against the packaged artifact: JSON request deserialization of the
// management endpoints, the Bearer mechanism next to the cookie mechanism, the token row Qute bindings on /config
// and the JSON responses of the token API.
@QuarkusIntegrationTest
class ApiTokenCheckIT {

  private val sessionCookie = SessionCookieForge.forgeSessionCookie("it-test-api-token-user")

  @Test
  fun `api token can be created, used, deactivated and deleted`() {
    val created = given()
      .cookie("spotify-session", sessionCookie)
      .contentType("application/json")
      .body("""{"name":"it-native-token","scopes":["READ"]}""")
      .`when`().post("/settings/api-tokens")
      .then().statusCode(200)
      .extract()
    val id: String = created.path("id")
    val token: String = created.path("token")

    given()
      .cookie("spotify-session", sessionCookie)
      .`when`().get("/config")
      .then().statusCode(200)
      .body(containsString("it-native-token"))

    given().auth().oauth2(token)
      .`when`().get("/api/v1/health")
      .then().statusCode(200)
      .body("mongoCollections", notNullValue())

    given().auth().oauth2(token)
      .`when`().get("/api/v1/token")
      .then().statusCode(200)
      .body("name", equalTo("api-token:it-native-token"))

    given()
      .cookie("spotify-session", sessionCookie)
      .contentType("application/json")
      .body("""{"active":false}""")
      .`when`().put("/settings/api-tokens/$id/active")
      .then().statusCode(200)

    given().auth().oauth2(token)
      .`when`().get("/api/v1/health")
      .then().statusCode(401)

    given()
      .cookie("spotify-session", sessionCookie)
      .`when`().delete("/settings/api-tokens/$id")
      .then().statusCode(200)
  }
}
