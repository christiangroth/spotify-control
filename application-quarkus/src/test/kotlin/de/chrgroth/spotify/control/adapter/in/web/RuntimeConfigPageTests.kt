package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-a")
class RuntimeConfigPageTests {

    @Test
    fun `runtime config page is available and displays heading`() {
        given()
            .`when`()
            .get("/settings/runtime-config")
            .then()
            .statusCode(200)
            .contentType(containsString("text/html"))
            .body(containsString("Runtime Config"))
    }

    @Test
    fun `runtime config page displays throttle interval input`() {
        given()
            .`when`()
            .get("/settings/runtime-config")
            .then()
            .statusCode(200)
            .body(containsString("""data-testid="throttle-interval-input""""))
    }

    @Test
    fun `runtime config page displays save and reset buttons`() {
        given()
            .`when`()
            .get("/settings/runtime-config")
            .then()
            .statusCode(200)
            .body(containsString("""data-testid="throttle-save-btn""""))
            .body(containsString("""data-testid="throttle-reset-btn""""))
    }

    @Test
    fun `runtime config page displays default throttle interval`() {
        given()
            .`when`()
            .get("/settings/runtime-config")
            .then()
            .statusCode(200)
            .body(containsString("""data-testid="default-throttle-interval""""))
            .body(containsString("10"))
    }

    @Test
    fun `runtime config page is accessible from nav`() {
        given()
            .`when`()
            .get("/settings/runtime-config")
            .then()
            .statusCode(200)
            .body(containsString("""data-testid="runtime-config-link""""))
    }

    @Test
    fun `update throttle interval endpoint accepts valid value`() {
        given()
            .contentType("application/json")
            .body("""{"intervalSeconds":5}""")
            .`when`()
            .post("/settings/runtime-config/throttle-interval")
            .then()
            .statusCode(200)
    }

    @Test
    fun `update throttle interval endpoint rejects negative value`() {
        given()
            .contentType("application/json")
            .body("""{"intervalSeconds":-1}""")
            .`when`()
            .post("/settings/runtime-config/throttle-interval")
            .then()
            .statusCode(400)
    }

    @Test
    fun `update throttle interval to zero is allowed`() {
        given()
            .contentType("application/json")
            .body("""{"intervalSeconds":0}""")
            .`when`()
            .post("/settings/runtime-config/throttle-interval")
            .then()
            .statusCode(200)
    }
}
