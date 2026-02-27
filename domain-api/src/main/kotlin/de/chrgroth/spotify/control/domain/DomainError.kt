package de.chrgroth.spotify.control.domain

sealed interface DomainError {
    val code: String
}

enum class AuthError(override val code: String) : DomainError {
    USER_NOT_ALLOWED("AUTH-001"),
    TOKEN_EXCHANGE_FAILED("AUTH-002"),
    PROFILE_FETCH_FAILED("AUTH-003"),
    UNEXPECTED("AUTH-999"),
    ;
}

enum class TokenError(override val code: String) : DomainError {
    ENCRYPTION_FAILED("TOKEN-001"),
    DECRYPTION_FAILED("TOKEN-002"),
    INVALID_FORMAT("TOKEN-003"),
    UNEXPECTED("TOKEN-999"),
    ;
}

enum class SpotifyError(override val code: String) : DomainError {
    TOKEN_REFRESH_FAILED("SPOTIFY-001"),
    UNEXPECTED("SPOTIFY-999"),
    ;
}

enum class UserError(override val code: String) : DomainError {
    NOT_FOUND("USER-001"),
    UNEXPECTED("USER-999"),
    ;
}
