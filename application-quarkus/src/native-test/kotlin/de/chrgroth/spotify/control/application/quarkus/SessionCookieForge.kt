package de.chrgroth.spotify.control.application.quarkus

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// @TestSecurity (used by the @QuarkusTest/JVM page tests) only fakes a SecurityIdentity in-process, which doesn't
// reach the packaged artifact exercised by @QuarkusIntegrationTest checks. Instead, a spotify-session cookie is
// forged with the fixed `%test.app.token-encryption-key` (see domain-impl/src/main/resources/application.properties,
// active here via `quarkus.test.integration-test-profile=test`), the same way SpotifyCookieAuthMechanism decrypts it.
internal object SessionCookieForge {
  private const val TEST_ENCRYPTION_KEY_BASE64 = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
  private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
  private const val GCM_IV_LENGTH = 12
  private const val GCM_TAG_LENGTH = 128

  fun forgeSessionCookie(userId: String): String {
    val secretKey = SecretKeySpec(Base64.getDecoder().decode(TEST_ENCRYPTION_KEY_BASE64), "AES")
    val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
    val ciphertext = cipher.doFinal(userId.toByteArray(Charsets.UTF_8))
    val encoder = Base64.getEncoder()
    return "${encoder.encodeToString(iv)}.${encoder.encodeToString(ciphertext)}"
  }
}
