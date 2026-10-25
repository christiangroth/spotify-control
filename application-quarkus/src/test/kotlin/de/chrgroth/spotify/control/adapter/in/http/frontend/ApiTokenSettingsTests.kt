package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.startsWith
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-a")
class ApiTokenSettingsTests {

  private fun createToken(name: String): String =
    given()
      .contentType("application/json")
      .body("""{"name":"$name","scopes":["READ"]}""")
      .`when`().post("/settings/api-tokens")
      .then().statusCode(200)
      .body("token", startsWith("spctl_"))
      .extract().path("id")

  @Test
  fun `created token is listed on the config page`() {
    createToken("listed-token")

    given()
      .`when`().get("/config")
      .then().statusCode(200)
      .body(containsString("""data-testid="api-tokens-table""""))
      .body(containsString("listed-token"))
  }

  @Test
  fun `token can be deactivated, rescoped and deleted`() {
    val id = createToken("managed-token")

    given().contentType("application/json").body("""{"active":false}""")
      .`when`().put("/settings/api-tokens/$id/active")
      .then().statusCode(200).body("status", equalTo("ok"))

    given().contentType("application/json").body("""{"scopes":["READ","WRITE"]}""")
      .`when`().put("/settings/api-tokens/$id/scopes")
      .then().statusCode(200)

    given()
      .`when`().delete("/settings/api-tokens/$id")
      .then().statusCode(200)

    given()
      .`when`().delete("/settings/api-tokens/$id")
      .then().statusCode(404)
  }

  @Test
  fun `create rejects blank name, missing and unknown scopes`() {
    given().contentType("application/json").body("""{"name":" ","scopes":["READ"]}""")
      .`when`().post("/settings/api-tokens")
      .then().statusCode(400)

    given().contentType("application/json").body("""{"name":"no-scopes","scopes":[]}""")
      .`when`().post("/settings/api-tokens")
      .then().statusCode(400)

    given().contentType("application/json").body("""{"name":"bad-scope","scopes":["ADMIN"]}""")
      .`when`().post("/settings/api-tokens")
      .then().statusCode(400)
  }

  @Test
  fun `cookie session cannot use the token api`() {
    given()
      .`when`().get("/api/v1/health")
      .then().statusCode(403)
  }
}
