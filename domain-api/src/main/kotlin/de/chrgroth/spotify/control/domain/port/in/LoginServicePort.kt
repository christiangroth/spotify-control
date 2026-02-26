package de.chrgroth.spotify.control.domain.port.`in`

import de.chrgroth.spotify.control.domain.model.UserId

sealed class LoginResult {
    data class Success(val userId: UserId) : LoginResult()
    data class Failure(val error: String) : LoginResult()
}

interface LoginServicePort {
    fun handleCallback(code: String): LoginResult
}
