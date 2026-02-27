package de.chrgroth.spotify.control.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DomainErrorUniquenessTest {

    private val allErrors: List<DomainError> = listOf(
        *AuthError.entries.toTypedArray(),
        *TokenError.entries.toTypedArray(),
        *SpotifyError.entries.toTypedArray(),
        *UserError.entries.toTypedArray(),
    )

    @Test
    fun `all DomainError codes are unique`() {
        val codes = allErrors.map { it.code }
        assertThat(codes).doesNotHaveDuplicates()
    }

    @Test
    fun `all DomainError codes follow prefix convention`() {
        allErrors.forEach { error ->
            assertThat(error.code).matches(Regex("[A-Z]+-\\d{3}").toPattern())
        }
    }
}
