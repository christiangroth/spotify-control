package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.AppAlbum
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.PlaybackEnrichmentPort
import de.chrgroth.spotify.control.domain.port.out.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAlbumDetailsPort
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
    private val spotifyAlbumDetails: SpotifyAlbumDetailsPort,
    private val appArtistRepository: AppArtistRepositoryPort,
    private val appTrackRepository: AppTrackRepositoryPort,
    private val appAlbumRepository: AppAlbumRepositoryPort,
    private val outboxPort: OutboxPort,
) : PlaybackEnrichmentPort {

    override fun enrichArtistDetails(artistId: String, userId: UserId): Either<DomainError, Unit> {
        val existing = appArtistRepository.findByArtistIds(setOf(artistId)).firstOrNull()
        if (existing?.lastEnrichmentDate != null) {
            logger.debug { "Artist $artistId already enriched, skipping" }
            return Unit.right()
        }
        logger.info { "Fetching genre details for artist $artistId (user ${userId.value})" }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyArtistDetails.getArtist(userId, accessToken, artistId)
            .flatMap { detail ->
                if (detail != null) {
                    appArtistRepository.updateEnrichmentData(detail.artistId, detail.artistName, detail.genres, detail.imageLink)
                    logger.info { "Updated enrichment data for artist $artistId: ${detail.genres}" }
                } else {
                    logger.warn { "No data returned from Spotify for artist $artistId" }
                }
                Unit.right()
            }
    }

    override fun enrichTrackDetails(trackId: String, userId: UserId): Either<DomainError, Unit> {
        val existing = appTrackRepository.findByTrackIds(setOf(trackId)).firstOrNull()
        if (existing?.lastEnrichmentDate != null) {
            logger.debug { "Track $trackId already enriched, skipping" }
            return Unit.right()
        }
        logger.info { "Fetching album id for track $trackId (user ${userId.value})" }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyTrackDetails.getTrack(userId, accessToken, trackId)
            .flatMap { albumId ->
                if (albumId != null) {
                    // Create stub album entry so it can be found; details filled by EnrichAlbumDetails
                    appAlbumRepository.upsertAll(listOf(AppAlbum(albumId = albumId)))
                    appTrackRepository.updateAlbumId(trackId, albumId)
                    // Enqueue album detail enrichment on to-spotify partition
                    outboxPort.enqueue(DomainOutboxEvent.EnrichAlbumDetails(albumId, userId))
                    logger.info { "Updated albumId for track $trackId → album $albumId" }
                } else {
                    logger.warn { "No data returned from Spotify for track $trackId" }
                }
                Unit.right()
            }
    }

    override fun enrichAlbumDetails(albumId: String, userId: UserId): Either<DomainError, Unit> {
        val existing = appAlbumRepository.findByAlbumIds(setOf(albumId)).firstOrNull()
        if (existing?.lastEnrichmentDate != null) {
            logger.debug { "Album $albumId already enriched, skipping" }
            return Unit.right()
        }
        logger.info { "Fetching album details for album $albumId (user ${userId.value})" }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyAlbumDetails.getAlbum(userId, accessToken, albumId)
            .flatMap { detail ->
                if (detail != null) {
                    appAlbumRepository.updateEnrichmentData(detail.albumId, detail.albumTitle, detail.imageLink, detail.genres, detail.artistId)
                    logger.info { "Updated enrichment data for album $albumId: '${detail.albumTitle}'" }
                } else {
                    logger.warn { "No data returned from Spotify for album $albumId" }
                }
                Unit.right()
            }
    }

    companion object : KLogging()
}
