package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.DomainResult

interface TokenEncryptionPort {
    fun encrypt(plaintext: String): DomainResult<String>
    fun decrypt(ciphertext: String): DomainResult<String>
}
