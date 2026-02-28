package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.raise.either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.RecentlyPlayedPort
import de.chrgroth.spotify.control.domain.port.out.RecentlyPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyRecentlyPlayedPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class RecentlyPlayedAdapter(
    private val userRepository: UserRepositoryPort,
    private val spotifyAccessToken: SpotifyAccessTokenPort,
    private val spotifyRecentlyPlayed: SpotifyRecentlyPlayedPort,
    private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort,
) : RecentlyPlayedPort {

    override fun fetchAndPersistForAllUsers() {
        val users = userRepository.findAll()
        logger.info { "Fetching recently played for ${users.size} user(s)" }
        users.forEach { user ->
            try {
                fetchAndPersistForUser(user.spotifyUserId).fold(
                    ifLeft = { logger.error { "Failed to fetch recently played for user ${user.spotifyUserId.value}: ${it.code}" } },
                    ifRight = { count -> logger.info { "Persisted $count new recently played item(s) for user ${user.spotifyUserId.value}" } },
                )
            } catch (e: Exception) {
                logger.error(e) { "Unexpected error fetching recently played for user ${user.spotifyUserId.value}" }
            }
        }
    }

    private fun fetchAndPersistForUser(userId: UserId): Either<DomainError, Int> = either {
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        val tracks = spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken).bind()
        val playedAts = tracks.map { it.playedAt }.toSet()
        val existingPlayedAts = recentlyPlayedRepository.findExistingPlayedAts(userId, playedAts)
        val newItems = tracks.filter { it.playedAt !in existingPlayedAts }
        if (newItems.isNotEmpty()) {
            logger.info { "Persisting ${newItems.size} new recently played items for user: ${userId.value}" }
            recentlyPlayedRepository.saveAll(newItems)
        }
        newItems.size
    }

    companion object : KLogging()
}
