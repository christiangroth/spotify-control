package de.chrgroth.spotify.control.domain.error

sealed interface DomainError {
    val code: String
}

enum class AuthError(override val code: String) : DomainError {
    USER_NOT_ALLOWED("AUTH-001"),
    TOKEN_EXCHANGE_FAILED("AUTH-002"),
    PROFILE_FETCH_FAILED("AUTH-003"),
    TOKEN_REFRESH_FAILED("AUTH-004"),
    UNEXPECTED("AUTH-099"),
    ;
}

enum class OAuthError(override val code: String) : DomainError {
    SPOTIFY_DENIED("OAUTH-001"),
    INVALID_REQUEST("OAUTH-002"),
    STATE_MISMATCH("OAUTH-003"),
    ;
}

enum class TokenError(override val code: String) : DomainError {
    ENCRYPTION_FAILED("TOKEN-001"),
    DECRYPTION_FAILED("TOKEN-002"),
    INVALID_FORMAT("TOKEN-003"),
    ;
}

enum class PlaybackError(override val code: String) : DomainError {
    RECENTLY_PLAYED_FETCH_FAILED("PLAY-001"),
    ;
}

enum class PlaylistSyncError(override val code: String) : DomainError {
    PLAYLIST_FETCH_FAILED("PLAYLIST-001"),
    PLAYLIST_NOT_FOUND("PLAYLIST-002"),
    PLAYLIST_TRACKS_FETCH_FAILED("PLAYLIST-003"),
    ;
}

data class SpotifyRateLimitError(val retryAfter: java.time.Duration) : DomainError {
    override val code: String = "SPOTIFY-429"
}
