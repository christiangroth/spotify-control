package de.chrgroth.spotify.control.domain.port.`in`

import de.chrgroth.spotify.control.domain.DomainResult
import de.chrgroth.spotify.control.domain.model.UserId

typealias LoginResult = DomainResult<UserId>

interface LoginServicePort {
    fun handleCallback(code: String): LoginResult
}
