package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.port.out.TokenEncryptionPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class TokenEncryptionAdapter(
    @ConfigProperty(name = "app.token-encryption-key")
    encryptionKeyBase64: String,
) : TokenEncryptionPort {

    private val secretKey = SecretKeySpec(Base64.getDecoder().decode(encryptionKeyBase64), "AES")
    private val random = SecureRandom()

    override fun encrypt(plaintext: String): DomainResult<String> = try {
        val iv = ByteArray(GCM_IV_LENGTH).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val encoder = Base64.getEncoder()
        DomainResult.Success("${encoder.encodeToString(iv)}.${encoder.encodeToString(ciphertext)}")
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error during token encryption" }
        DomainResult.Failure(TokenError.ENCRYPTION_FAILED)
    }

    override fun decrypt(ciphertext: String): DomainResult<String> {
        return try {
            val parts = ciphertext.split(".")
            if (parts.size != 2) {
                DomainResult.Failure(TokenError.INVALID_FORMAT)
            } else {
                val decoder = Base64.getDecoder()
                val iv = decoder.decode(parts[0])
                val encrypted = decoder.decode(parts[1])
                val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                DomainResult.Success(String(cipher.doFinal(encrypted), Charsets.UTF_8))
            }
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during token decryption" }
            DomainResult.Failure(TokenError.DECRYPTION_FAILED)
        }
    }

    companion object : KLogging() {
        private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }
}
