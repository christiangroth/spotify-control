package de.chrgroth.spotify.control.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TokenEncryptionAdapterTests {

    // 32-byte key encoded as base64 (all zeros for testing)
    private val testKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val adapter = TokenEncryptionAdapter(testKey)

    @Test
    fun `encrypt and decrypt round-trip succeeds`() {
        val plaintext = "hello-world"
        val ciphertext = adapter.encrypt(plaintext)
        assertThat(adapter.decrypt(ciphertext)).isEqualTo(plaintext)
    }

    @Test
    fun `encrypt produces different ciphertexts for the same input`() {
        val plaintext = "same-input"
        val first = adapter.encrypt(plaintext)
        val second = adapter.encrypt(plaintext)
        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `decrypt throws for invalid format`() {
        assertThatThrownBy { adapter.decrypt("not-a-valid-ciphertext") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `decrypt throws for tampered ciphertext`() {
        val ciphertext = adapter.encrypt("sensitive")
        val tampered = ciphertext.dropLast(4) + "XXXX"
        assertThatThrownBy { adapter.decrypt(tampered) }
            .isInstanceOf(javax.crypto.AEADBadTagException::class.java)
    }
}
