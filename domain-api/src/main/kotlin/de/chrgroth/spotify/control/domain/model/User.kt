package de.chrgroth.spotify.control.domain.model

import kotlin.time.Instant

data class User(
    val spotifyUserId: String,
    val displayName: String,
    val encryptedAccessToken: String,
    val encryptedRefreshToken: String,
    val tokenExpiresAt: Instant,
    val createdAt: Instant,
    val lastLoginAt: Instant,
)
