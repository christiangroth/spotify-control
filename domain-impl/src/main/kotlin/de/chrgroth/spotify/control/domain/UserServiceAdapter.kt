package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.UserServicePort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class UserServiceAdapter(
    @ConfigProperty(name = "app.allowed-spotify-user-ids")
    allowedUserIdStrings: List<String>,
) : UserServicePort {

    private val allowedUserIds: Set<UserId> = allowedUserIdStrings.map { UserId(it) }.toSet()

    override fun isAllowed(userId: UserId): Boolean {
        val allowed = userId in allowedUserIds
        if (!allowed) {
            logger.warn { "User not in allowed list: ${userId.value}" }
        }
        return allowed
    }

    companion object : KLogging()
}
