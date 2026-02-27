package de.chrgroth.spotify.control.adapter.web.`in`

import de.chrgroth.spotify.control.domain.port.out.TokenEncryptionPort
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.junit.jupiter.api.Test

@QuarkusTest
class OAuthFlowTests {

    @Inject
    private lateinit var tokenEncryption: TokenEncryptionPort

    @Test
    fun `authorize endpoint redirects to spotify mock`() {
        given()
            .redirects().follow(false)
            .`when`()
            .get("/oauth/authorize")
            .then()
            .statusCode(307)
            .header("Location", org.hamcrest.CoreMatchers.containsString("http://localhost:8081/mock/spotify/authorize"))
    }

    @Test
    fun `callback with error redirects to login`() {
        given()
            .redirects().follow(false)
            .queryParam("error", "access_denied")
            .`when`()
            .get("/oauth/callback")
            .then()
            .statusCode(307)
            .header("Location", org.hamcrest.CoreMatchers.containsString("error=spotify_denied"))
    }

    @Test
    fun `callback with missing params redirects to login`() {
        given()
            .redirects().follow(false)
            .`when`()
            .get("/oauth/callback")
            .then()
            .statusCode(307)
            .header("Location", org.hamcrest.CoreMatchers.containsString("error=invalid_request"))
    }

    @Test
    fun `callback with invalid state redirects to login`() {
        given()
            .redirects().follow(false)
            .queryParam("code", "some-code")
            .queryParam("state", "invalid-state")
            .`when`()
            .get("/oauth/callback")
            .then()
            .statusCode(307)
            .header("Location", org.hamcrest.CoreMatchers.containsString("error=state_mismatch"))
    }

    @Test
    fun `full oauth callback flow succeeds for allowed user`() {
        // Trigger authorize to get a valid state
        val authorizeResponse = given()
            .redirects().follow(false)
            .`when`()
            .get("/oauth/authorize")
            .then()
            .statusCode(307)
            .extract()
            .response()

        val location = authorizeResponse.getHeader("Location")
        val state = location.substringAfter("state=").substringBefore("&")

        // Simulate callback with valid state
        val callbackResponse = given()
            .redirects().follow(false)
            .queryParam("code", "mock-auth-code")
            .queryParam("state", state)
            .`when`()
            .get("/oauth/callback")
            .then()
            .statusCode(307)
            .header("Location", org.hamcrest.CoreMatchers.endsWith("/ui/dashboard"))
            .extract()
            .response()

        // Session cookie should be set
        val sessionCookie = callbackResponse.cookie("spotify-session")
        assert(sessionCookie != null && sessionCookie.isNotEmpty())
    }

    @Test
    fun `logout clears session and redirects to root`() {
        val cookieValue = (tokenEncryption.encrypt("test-user-a") as arrow.core.Either.Right).value

        given()
            .cookie("spotify-session", cookieValue)
            .redirects().follow(false)
            .`when`()
            .get("/logout")
            .then()
            .statusCode(307)
            .header("Location", org.hamcrest.CoreMatchers.endsWith("/"))
    }

    @Test
    fun `protected route redirects to login without session`() {
        given()
            .redirects().follow(false)
            .`when`()
            .get("/ui/dashboard")
            .then()
            .statusCode(307)
            .header("Location", org.hamcrest.CoreMatchers.endsWith("/"))
    }
}

