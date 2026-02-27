package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.raise.either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.FetchRecentlyPlayedPort
import de.chrgroth.spotify.control.domain.port.out.RecentlyPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyRecentlyPlayedPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class FetchRecentlyPlayedAdapter(
    private val spotifyAccessToken: SpotifyAccessTokenPort,
    private val spotifyRecentlyPlayed: SpotifyRecentlyPlayedPort,
    private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort,
) : FetchRecentlyPlayedPort {

    override fun fetchAndPersist(userId: UserId): Either<DomainError, Int> = either {
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
