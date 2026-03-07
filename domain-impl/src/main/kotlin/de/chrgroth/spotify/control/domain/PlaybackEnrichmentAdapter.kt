package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.AppAlbum
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.PlaybackEnrichmentPort
import de.chrgroth.spotify.control.domain.port.out.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyArtistDetailsPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyTrackDetailsPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class PlaybackEnrichmentAdapter(
    private val spotifyAccessToken: SpotifyAccessTokenPort,
    private val spotifyArtistDetails: SpotifyArtistDetailsPort,
    private val spotifyTrackDetails: SpotifyTrackDetailsPort,
    private val appArtistRepository: AppArtistRepositoryPort,
    private val appTrackRepository: AppTrackRepositoryPort,
    private val appAlbumRepository: AppAlbumRepositoryPort,
) : PlaybackEnrichmentPort {

    override fun enrichArtistDetails(artistId: String, userId: UserId): Either<DomainError, Unit> {
        val existing = appArtistRepository.findByArtistIds(setOf(artistId)).firstOrNull()
        if (existing != null && existing.genres.isNotEmpty()) {
            logger.debug { "Artist $artistId already has genres, skipping enrichment" }
            return Unit.right()
        }
        logger.info { "Fetching genre details for artist $artistId (user ${userId.value})" }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyArtistDetails.getArtist(userId, accessToken, artistId)
            .flatMap { detail ->
                if (detail != null) {
                    appArtistRepository.updateGenres(detail.artistId, detail.genres)
                    logger.info { "Updated genres for artist $artistId: ${detail.genres}" }
                } else {
                    logger.warn { "No data returned from Spotify for artist $artistId" }
                }
                Unit.right()
            }
    }

    override fun enrichTrackDetails(trackId: String, userId: UserId): Either<DomainError, Unit> {
        val existing = appTrackRepository.findByTrackIds(setOf(trackId)).firstOrNull()
        if (existing != null && existing.albumId != null) {
            logger.debug { "Track $trackId already has albumId, skipping enrichment" }
            return Unit.right()
        }
        logger.info { "Fetching album details for track $trackId (user ${userId.value})" }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyTrackDetails.getTrack(userId, accessToken, trackId)
            .flatMap { detail ->
                if (detail != null) {
                    appAlbumRepository.upsertAll(
                        listOf(
                            AppAlbum(
                                albumId = detail.albumId,
                                albumTitle = detail.albumTitle,
                                imageLink = detail.albumImageUrl,
                            ),
                        ),
                    )
                    appTrackRepository.updateAlbumId(detail.trackId, detail.albumId)
                    logger.info { "Updated albumId for track $trackId → album ${detail.albumId}" }
                } else {
                    logger.warn { "No data returned from Spotify for track $trackId" }
                }
                Unit.right()
            }
    }

    companion object : KLogging()
}
