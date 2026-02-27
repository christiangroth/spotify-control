package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.port.`in`.FetchAllRecentlyPlayedPort
import de.chrgroth.spotify.control.domain.port.`in`.FetchRecentlyPlayedPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class FetchAllRecentlyPlayedAdapter(
    private val userRepository: UserRepositoryPort,
    private val fetchRecentlyPlayed: FetchRecentlyPlayedPort,
) : FetchAllRecentlyPlayedPort {

    override fun fetchAndPersistForAllUsers() {
        val users = userRepository.findAll()
        logger.info { "Fetching recently played for ${users.size} user(s)" }
        users.forEach { user ->
            try {
                fetchRecentlyPlayed.fetchAndPersist(user.spotifyUserId).fold(
                    ifLeft = { logger.error { "Failed to fetch recently played for user ${user.spotifyUserId.value}: ${it.code}" } },
                    ifRight = { count -> logger.info { "Persisted $count new recently played item(s) for user ${user.spotifyUserId.value}" } },
                )
            } catch (e: Exception) {
                logger.error(e) { "Unexpected error fetching recently played for user ${user.spotifyUserId.value}" }
            }
        }
    }

    companion object : KLogging()
}
