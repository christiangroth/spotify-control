package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import de.chrgroth.outbox.OutboxTaskResult
import de.chrgroth.spotify.control.domain.error.ArtistSettingsError
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.model.AppAlbum
import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.CatalogPort
import de.chrgroth.spotify.control.domain.port.out.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyCatalogPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class CatalogAdapter(
    private val spotifyAccessToken: SpotifyAccessTokenPort,
    private val spotifyCatalog: SpotifyCatalogPort,
    private val appArtistRepository: AppArtistRepositoryPort,
    private val appTrackRepository: AppTrackRepositoryPort,
    private val appAlbumRepository: AppAlbumRepositoryPort,
    private val appPlaybackRepository: AppPlaybackRepositoryPort,
    private val userRepository: UserRepositoryPort,
    private val outboxPort: OutboxPort,
) : CatalogPort {

    // --- Artist Settings ---

    override fun findAllArtists(): List<AppArtist> = appArtistRepository.findAll()

    override fun updateArtistPlaybackProcessingStatus(
        artistId: String,
        status: ArtistPlaybackProcessingStatus,
        userId: UserId,
    ): Either<DomainError, Unit> {
        val existing = appArtistRepository.findByArtistIds(setOf(artistId)).firstOrNull()
            ?: return ArtistSettingsError.ARTIST_NOT_FOUND.left()

        if (existing.playbackProcessingStatus == status) {
            logger.debug { "Artist $artistId already has status $status, skipping" }
            return Unit.right()
        }

        logger.info { "Updating playback processing status for artist $artistId to $status" }
        appArtistRepository.updatePlaybackProcessingStatus(artistId, status)

        when (status) {
            ArtistPlaybackProcessingStatus.INACTIVE -> {
                val trackIds = appTrackRepository.findByArtistId(artistId).map { it.trackId }.toSet()
                if (trackIds.isNotEmpty()) {
                    logger.info { "Deleting app_playback for ${trackIds.size} tracks of artist $artistId" }
                    appPlaybackRepository.deleteAllByTrackIds(trackIds)
                }
            }
            ArtistPlaybackProcessingStatus.ACTIVE, ArtistPlaybackProcessingStatus.UNDECIDED -> {
                if (existing.playbackProcessingStatus == ArtistPlaybackProcessingStatus.INACTIVE) {
                    logger.info { "Artist $artistId reactivated, enqueueing RebuildPlaybackData for all users" }
                    userRepository.findAll().forEach { user ->
                        outboxPort.enqueue(DomainOutboxEvent.RebuildPlaybackData(user.spotifyUserId))
                    }
                }
            }
        }

        return Unit.right()
    }

    // --- Enrichment ---

    override fun enrichArtistDetails(artistId: String, userId: UserId): Either<DomainError, Unit> {
        val existing = appArtistRepository.findByArtistIds(setOf(artistId)).firstOrNull()
        if (existing?.lastEnrichmentDate != null && existing.artistName.isNotBlank()) {
            logger.debug { "Artist $artistId already enriched, skipping" }
            return Unit.right()
        }
        logger.info { "Fetching genre details for artist $artistId (user ${userId.value})" }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyCatalog.getArtist(userId, accessToken, artistId)
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
        return spotifyCatalog.getTrack(userId, accessToken, trackId)
            .flatMap { albumId ->
                if (albumId != null) {
                    appAlbumRepository.upsertAll(listOf(AppAlbum(albumId = albumId)))
                    appTrackRepository.updateAlbumId(trackId, albumId)
                    outboxPort.enqueue(DomainOutboxEvent.EnrichAlbumDetails(albumId, userId))
                    logger.info { "Updated albumId for track $trackId → album $albumId" }
                } else {
                    logger.warn { "No data returned from Spotify for track $trackId" }
                }
                Unit.right()
            }
    }

    override fun enrichTrackDetailsBulk(trackIds: List<String>, userId: UserId): Either<DomainError, Unit> {
        val existing = appTrackRepository.findByTrackIds(trackIds.toSet())
        val alreadyEnrichedIds = existing.filter { it.lastEnrichmentDate != null }.map { it.trackId }.toSet()
        val unenrichedIds = trackIds.filter { it !in alreadyEnrichedIds }
        if (unenrichedIds.isEmpty()) {
            logger.debug { "All ${trackIds.size} tracks already enriched, skipping bulk fetch" }
            return Unit.right()
        }
        logger.info { "Bulk-fetching album ids for ${unenrichedIds.size} tracks (user ${userId.value})" }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyCatalog.getTracks(userId, accessToken, unenrichedIds)
            .flatMap { albumIdByTrackId ->
                albumIdByTrackId.forEach { (trackId, albumId) ->
                    if (albumId != null) {
                        appAlbumRepository.upsertAll(listOf(AppAlbum(albumId = albumId)))
                        appTrackRepository.updateAlbumId(trackId, albumId)
                        outboxPort.enqueue(DomainOutboxEvent.EnrichAlbumDetails(albumId, userId))
                        logger.info { "Updated albumId for track $trackId → album $albumId" }
                    } else {
                        logger.warn { "No album data returned from Spotify for track $trackId" }
                    }
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
        return spotifyCatalog.getAlbum(userId, accessToken, albumId)
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

    // --- Outbox Handlers ---

    override fun handle(event: DomainOutboxEvent.EnrichArtistDetails): OutboxTaskResult = try {
        when (val result = enrichArtistDetails(event.artistId, event.userId)) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> {
                    logger.warn { "Rate limited on EnrichArtistDetails artist ${event.artistId} (user ${event.userId.value}), retry after ${error.retryAfter.seconds}s" }
                    OutboxTaskResult.RateLimited(error.retryAfter)
                }
                else -> {
                    logger.error { "Failed to enrich artist ${event.artistId} for user ${event.userId.value}: ${error.code}" }
                    OutboxTaskResult.Failed("Failed to enrich artist: ${error.code}")
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(EnrichArtistDetails) for artist ${event.artistId} (user ${event.userId.value})" }
        OutboxTaskResult.Failed("Unexpected error in enrich: ${e.message}", e)
    }

    override fun handle(event: DomainOutboxEvent.EnrichTrackDetails): OutboxTaskResult = try {
        when (val result = enrichTrackDetails(event.trackId, event.userId)) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> {
                    logger.warn { "Rate limited on EnrichTrackDetails for track ${event.trackId} (user ${event.userId.value}), retry after ${error.retryAfter.seconds}s" }
                    OutboxTaskResult.RateLimited(error.retryAfter)
                }
                else -> {
                    logger.error { "Failed to enrich track ${event.trackId} for user ${event.userId.value}: ${error.code}" }
                    OutboxTaskResult.Failed("Failed to enrich track: ${error.code}")
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(EnrichTrackDetails) for track ${event.trackId} (user ${event.userId.value})" }
        OutboxTaskResult.Failed("Unexpected error in enrich: ${e.message}", e)
    }

    override fun handle(event: DomainOutboxEvent.EnrichTrackDetailsBulk): OutboxTaskResult = try {
        when (val result = enrichTrackDetailsBulk(event.trackIds, event.userId)) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> {
                    logger.warn { "Rate limited on EnrichTrackDetailsBulk for ${event.trackIds.size} tracks (user ${event.userId.value}), retry after ${error.retryAfter.seconds}s" }
                    OutboxTaskResult.RateLimited(error.retryAfter)
                }
                else -> {
                    logger.error { "Failed to bulk-enrich ${event.trackIds.size} tracks for user ${event.userId.value}: ${error.code}" }
                    OutboxTaskResult.Failed("Failed to bulk-enrich tracks: ${error.code}")
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(EnrichTrackDetailsBulk) for ${event.trackIds.size} tracks (user ${event.userId.value})" }
        OutboxTaskResult.Failed("Unexpected error in bulk enrich: ${e.message}", e)
    }

    override fun handle(event: DomainOutboxEvent.EnrichAlbumDetails): OutboxTaskResult = try {
        when (val result = enrichAlbumDetails(event.albumId, event.userId)) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> {
                    logger.warn { "Rate limited on EnrichAlbumDetails for album ${event.albumId} (user ${event.userId.value}), retry after ${error.retryAfter.seconds}s" }
                    OutboxTaskResult.RateLimited(error.retryAfter)
                }
                else -> {
                    logger.error { "Failed to enrich album ${event.albumId} for user ${event.userId.value}: ${error.code}" }
                    OutboxTaskResult.Failed("Failed to enrich album: ${error.code}")
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(EnrichAlbumDetails) for album ${event.albumId} (user ${event.userId.value})" }
        OutboxTaskResult.Failed("Unexpected error in enrich: ${e.message}", e)
    }

    companion object : KLogging()
}
