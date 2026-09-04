package de.chrgroth.spotify.control.application.quarkus

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// @TestSecurity (used by DashboardPageTests for the @QuarkusTest/JVM run) only fakes a SecurityIdentity
// in-process, which doesn't reach the packaged artifact exercised here. Instead, a spotify-session cookie is
// forged with the fixed `%test.app.token-encryption-key` (see domain-impl/src/main/resources/application.properties,
// active here via `quarkus.test.integration-test-profile=test`), the same way SpotifyCookieAuthMechanism decrypts
// it. This renders the real /dashboard page (native executable when invoked via `testNative`), so a Qute
// reflection gap that native-image analysis misses on untyped `Template.data()` bindings (see #886/#888) is
// caught in CI before merge instead of surfacing in production.
@QuarkusIntegrationTest
class DashboardCheckIT {

  @Test
  fun `dashboard page renders successfully for a fresh user`() {
    given()
      .cookie("spotify-session", forgeSessionCookie("it-test-user"))
      .`when`()
      .get("/dashboard")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("""data-testid="welcome-message""""))
      .body(containsString("""data-testid="histogram""""))
  }

  companion object {
    private const val TEST_ENCRYPTION_KEY_BASE64 = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
    private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    private fun forgeSessionCookie(userId: String): String {
      val secretKey = SecretKeySpec(Base64.getDecoder().decode(TEST_ENCRYPTION_KEY_BASE64), "AES")
      val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
      val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
      val ciphertext = cipher.doFinal(userId.toByteArray(Charsets.UTF_8))
      val encoder = Base64.getEncoder()
      return "${encoder.encodeToString(iv)}.${encoder.encodeToString(ciphertext)}"
    }
  }
}
