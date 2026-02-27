package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.port.`in`.UserProfileUpdatePort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAuthPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class UserProfileUpdateAdapter(
    private val userRepository: UserRepositoryPort,
    private val spotifyAccessToken: SpotifyAccessTokenPort,
    private val spotifyAuth: SpotifyAuthPort,
) : UserProfileUpdatePort {

    override fun updateUserProfiles() {
        val users = userRepository.findAll()
        logger.info { "Updating profiles for ${users.size} user(s)" }
        users.forEach { user ->
            try {
                val accessToken = spotifyAccessToken.getValidAccessToken(user.spotifyUserId)
                spotifyAuth.getUserProfile(accessToken).fold(
                    ifLeft = { logger.error { "Failed to fetch profile for user ${user.spotifyUserId.value}: ${it.code}" } },
                    ifRight = { profile ->
                        if (profile.displayName != user.displayName) {
                            logger.info { "Updating displayName for user ${user.spotifyUserId.value}" }
                            userRepository.upsert(user.copy(displayName = profile.displayName))
                        }
                    },
                )
            } catch (e: Exception) {
                logger.error(e) { "Unexpected error updating profile for user ${user.spotifyUserId.value}" }
            }
        }
    }

    companion object : KLogging()
}
