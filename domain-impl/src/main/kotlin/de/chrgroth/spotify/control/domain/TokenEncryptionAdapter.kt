package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.port.out.TokenEncryptionPort
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@ApplicationScoped
@Suppress("Unused")
class TokenEncryptionAdapter(
    @ConfigProperty(name = "app.token-encryption-key")
    encryptionKeyBase64: String,
) : TokenEncryptionPort {

    private val secretKey = SecretKeySpec(Base64.getDecoder().decode(encryptionKeyBase64), "AES")
    private val random = SecureRandom()

    override fun encrypt(plaintext: String): String {
        val iv = ByteArray(GCM_IV_LENGTH).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val encoder = Base64.getEncoder()
        return "${encoder.encodeToString(iv)}.${encoder.encodeToString(ciphertext)}"
    }

    override fun decrypt(ciphertext: String): String {
        val parts = ciphertext.split(".")
        require(parts.size == 2) { "Invalid ciphertext format" }
        val decoder = Base64.getDecoder()
        val iv = decoder.decode(parts[0])
        val encrypted = decoder.decode(parts[1])
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    companion object {
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }
}
