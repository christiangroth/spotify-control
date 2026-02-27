package de.chrgroth.spotify.control.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TokenEncryptionAdapterTests {

    // 32-byte key encoded as base64 (all zeros for testing)
    private val testKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val adapter = TokenEncryptionAdapter(testKey)

    @Test
    fun `encrypt and decrypt round-trip succeeds`() {
        val plaintext = "hello-world"
        val encrypted = adapter.encrypt(plaintext)
        assertThat(encrypted).isInstanceOf(DomainResult.Success::class.java)
        val decrypted = adapter.decrypt((encrypted as DomainResult.Success).value)
        assertThat(decrypted).isInstanceOf(DomainResult.Success::class.java)
        assertThat((decrypted as DomainResult.Success).value).isEqualTo(plaintext)
    }

    @Test
    fun `encrypt produces different ciphertexts for the same input`() {
        val plaintext = "same-input"
        val first = adapter.encrypt(plaintext)
        val second = adapter.encrypt(plaintext)
        assertThat(first).isInstanceOf(DomainResult.Success::class.java)
        assertThat(second).isInstanceOf(DomainResult.Success::class.java)
        assertThat((first as DomainResult.Success).value).isNotEqualTo((second as DomainResult.Success).value)
    }

    @Test
    fun `decrypt returns invalid format failure for malformed ciphertext`() {
        val result = adapter.decrypt("not-a-valid-ciphertext")
        assertThat(result).isInstanceOf(DomainResult.Failure::class.java)
        assertThat((result as DomainResult.Failure).error).isEqualTo(TokenError.INVALID_FORMAT)
    }

    @Test
    fun `decrypt returns decryption failure for tampered ciphertext`() {
        val encrypted = adapter.encrypt("sensitive")
        assertThat(encrypted).isInstanceOf(DomainResult.Success::class.java)
        val tampered = (encrypted as DomainResult.Success).value.dropLast(4) + "XXXX"
        val result = adapter.decrypt(tampered)
        assertThat(result).isInstanceOf(DomainResult.Failure::class.java)
        assertThat((result as DomainResult.Failure).error).isEqualTo(TokenError.DECRYPTION_FAILED)
    }
}
