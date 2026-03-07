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

    override fun enrichArtistData(userId: UserId): Either<DomainError, Unit> {
        val artistsToEnrich = appArtistRepository.findNeedingGenreEnrichment()
        if (artistsToEnrich.isEmpty()) {
            logger.info { "No artists need genre enrichment" }
            return Unit.right()
        }
        logger.info { "Enriching ${artistsToEnrich.size} artists with genre data using token for user ${userId.value}" }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyArtistDetails.getArtists(userId, accessToken, artistsToEnrich.map { it.artistId })
            .flatMap { details ->
                details.forEach { detail ->
                    appArtistRepository.updateGenres(detail.artistId, detail.genres)
                }
                logger.info { "Updated genres for ${details.size} artists" }
                Unit.right()
            }
    }

    override fun enrichTrackData(userId: UserId): Either<DomainError, Unit> {
        val tracksToEnrich = appTrackRepository.findNeedingAlbumEnrichment()
        if (tracksToEnrich.isEmpty()) {
            logger.info { "No tracks need album enrichment" }
            return Unit.right()
        }
        logger.info { "Enriching ${tracksToEnrich.size} tracks with album data using token for user ${userId.value}" }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyTrackDetails.getTracks(userId, accessToken, tracksToEnrich.map { it.trackId })
            .flatMap { details ->
                val albums = details.mapNotNull { detail ->
                    detail.albumId.let { albumId ->
                        AppAlbum(
                            albumId = albumId,
                            albumTitle = detail.albumTitle,
                            imageLink = detail.albumImageUrl,
                        )
                    }
                }
                if (albums.isNotEmpty()) {
                    appAlbumRepository.upsertAll(albums)
                }
                details.forEach { detail ->
                    appTrackRepository.updateAlbumId(detail.trackId, detail.albumId)
                }
                logger.info { "Updated albumId for ${details.size} tracks and upserted ${albums.size} app_album entries" }
                Unit.right()
            }
    }

    companion object : KLogging()
}
