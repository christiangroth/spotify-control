package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.UserServicePort
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class UserServiceAdapter(
    @ConfigProperty(name = "app.allowed-spotify-user-ids")
    private val allowedUserIdStrings: List<String>,
) : UserServicePort {

    private val allowedUserIds: Set<UserId> = allowedUserIdStrings.map { UserId(it) }.toSet()

    override fun isAllowed(userId: UserId): Boolean = userId in allowedUserIds
}
