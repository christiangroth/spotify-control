package de.chrgroth.spotify.control.domain.port.out

interface TokenEncryptionPort {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}
