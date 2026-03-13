package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import de.chrgroth.outbox.OutboxTaskResult
import de.chrgroth.spotify.control.domain.error.ArtistSettingsError
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.AccessToken
import de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus
import de.chrgroth.spotify.control.domain.model.ArtistId
import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.CatalogPort
import de.chrgroth.spotify.control.domain.port.out.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppSyncPoolRepositoryPort
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
    private val syncPoolRepository: AppSyncPoolRepositoryPort,
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
                val trackIds = appTrackRepository.findByArtistId(ArtistId(artistId)).map { it.id.value }.toSet()
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

    // --- Catalog Sync ---

    override fun syncArtistDetails(artistId: String, userId: UserId): Either<DomainError, Unit> {
        val existing = appArtistRepository.findByArtistIds(setOf(artistId)).firstOrNull()
        if (existing?.lastSync != null && existing.artistName.isNotBlank()) {
            logger.debug { "Artist $artistId already synced, skipping" }
            return Unit.right()
        }
        logger.info { "Fetching genre details for artist $artistId (user ${userId.value})" }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyCatalog.getArtist(userId, accessToken, artistId)
            .flatMap { detail ->
                if (detail != null) {
                    appArtistRepository.updateSyncData(detail.artistId, detail.artistName, detail.genre, detail.additionalGenres, detail.imageLink, detail.type)
                    logger.info { "Updated sync data for artist $artistId: genre=${detail.genre}, additionalGenres=${detail.additionalGenres}" }
                } else {
                    logger.warn { "No data returned from Spotify for artist $artistId" }
                }
                Unit.right()
            }
    }

    override fun syncTrackDetails(trackId: String, userId: UserId): Either<DomainError, Unit> {
        val existing = appTrackRepository.findByTrackIds(setOf(TrackId(trackId))).firstOrNull()
        if (existing?.lastSync != null) {
            logger.debug { "Track $trackId already synced, skipping" }
            return Unit.right()
        }
        logger.info { "Fetching track/album details for track $trackId (user ${userId.value})" }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyCatalog.getTrack(userId, accessToken, trackId)
            .flatMap { result ->
                if (result != null) {
                    appTrackRepository.updateTrackSyncData(result.track)
                    appAlbumRepository.upsertAll(listOf(result.album))
                    val allArtistIds = (listOf(result.track.artistId) + result.track.additionalArtistIds)
                        .map { it.value }
                        .filter { it.isNotBlank() }
                    allArtistIds.forEach { artistId ->
                        outboxPort.enqueue(DomainOutboxEvent.SyncArtistDetails(artistId, userId))
                    }
                    logger.info { "Updated sync data for track $trackId → album ${result.album.id.value}" }
                } else {
                    logger.warn { "No data returned from Spotify for track $trackId" }
                }
                Unit.right()
            }
    }

    override fun resyncCatalog(): Either<DomainError, Unit> {
        val allArtistIds = appArtistRepository.findAll().map { it.artistId }
        val allTrackIds = appTrackRepository.findAll().map { it.id.value }
        logger.info { "Re-syncing catalog: ${allArtistIds.size} artist(s) and ${allTrackIds.size} track(s) added to sync pool" }
        if (allArtistIds.isNotEmpty()) syncPoolRepository.addArtists(allArtistIds)
        if (allTrackIds.isNotEmpty()) syncPoolRepository.addTracks(allTrackIds)
        return Unit.right()
    }

    override fun resyncArtist(artistId: String): Either<DomainError, Unit> {
        appArtistRepository.findByArtistIds(setOf(artistId)).firstOrNull()
            ?: return ArtistSettingsError.ARTIST_NOT_FOUND.left()
        logger.info { "Re-syncing artist $artistId and all their tracks" }
        syncPoolRepository.addArtists(listOf(artistId))
        val trackIds = appTrackRepository.findByArtistId(ArtistId(artistId)).map { it.id.value }
        if (trackIds.isNotEmpty()) syncPoolRepository.addTracks(trackIds)
        return Unit.right()
    }

    private fun syncMissingArtists(): Either<DomainError, Int> {        val userId = userRepository.findAll().firstOrNull()?.spotifyUserId
        if (userId == null) {
            logger.debug { "No users available, skipping syncMissingArtists" }
            return 0.right()
        }
        val artistIds = syncPoolRepository.peekArtists(BULK_LIMIT)
        if (artistIds.isEmpty()) {
            logger.debug { "No artists in sync pool" }
            return 0.right()
        }
        logger.info { "Syncing ${artistIds.size} missing artists from pool" }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyCatalog.getArtists(userId, accessToken, artistIds)
            .flatMap { artists ->
                if (artists.isNotEmpty()) {
                    appArtistRepository.upsertAll(artists)
                    val syncedIds = artists.map { it.artistId }
                    syncPoolRepository.removeArtists(syncedIds)
                    logger.info { "Synced ${artists.size} artists; ${artistIds.size - artists.size} not returned by Spotify will be retried" }
                }
                artists.size.right()
            }
    }

    private fun syncMissingTracks(): Either<DomainError, Int> {
        val userId = userRepository.findAll().firstOrNull()?.spotifyUserId
        if (userId == null) {
            logger.debug { "No users available, skipping syncMissingTracks" }
            return 0.right()
        }
        val trackIds = syncPoolRepository.peekTracks(BULK_LIMIT)
        if (trackIds.isEmpty()) {
            logger.debug { "No tracks in sync pool" }
            return 0.right()
        }
        logger.info { "Syncing ${trackIds.size} missing tracks from pool" }
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        val existingTracks = appTrackRepository.findByTrackIds(trackIds.map { TrackId(it) }.toSet())
            .associateBy { it.id.value }
        val albumGroups = trackIds
            .mapNotNull { trackId -> existingTracks[trackId]?.albumId?.let { albumId -> albumId.value to trackId } }
            .groupBy({ it.first }, { it.second })
        val tracksWithoutAlbum = trackIds.filter { existingTracks[it]?.albumId == null }.toMutableList()
        return processAlbumGroups(userId, accessToken, albumGroups, tracksWithoutAlbum)
            .flatMap { albumSynced -> syncDirectTracks(userId, accessToken, tracksWithoutAlbum, albumSynced) }
    }

    private fun processAlbumGroups(
        userId: UserId,
        accessToken: AccessToken,
        albumGroups: Map<String, List<String>>,
        tracksWithoutAlbum: MutableList<String>,
    ): Either<DomainError, Int> {
        var totalSynced = 0
        for ((albumId, albumTrackIds) in albumGroups) {
            val result = spotifyCatalog.getAlbumTracks(userId, accessToken, albumId)
            when (result) {
                is Either.Left -> return result.value.left()
                is Either.Right -> {
                    val allAlbumResults = result.value
                    if (allAlbumResults.isNotEmpty()) {
                        appTrackRepository.upsertAll(allAlbumResults.map { it.track })
                        appAlbumRepository.upsertAll(listOf(allAlbumResults.first().album))
                        val artistIds = allAlbumResults
                            .flatMap { r -> (listOf(r.track.artistId) + r.track.additionalArtistIds).map { it.value } }
                            .filter { it.isNotBlank() }.distinct()
                        if (artistIds.isNotEmpty()) syncPoolRepository.addArtists(artistIds)
                    }
                    val returnedTrackIds = allAlbumResults.map { it.track.id.value }.toSet()
                    val synced = albumTrackIds.filter { it in returnedTrackIds }
                    val notFound = albumTrackIds.filter { it !in returnedTrackIds }
                    if (synced.isNotEmpty()) {
                        syncPoolRepository.removeTracks(synced)
                        totalSynced += synced.size
                    }
                    tracksWithoutAlbum.addAll(notFound)
                }
            }
        }
        logger.info { "Synced $totalSynced tracks via ${albumGroups.size} album(s)" }
        return totalSynced.right()
    }

    private fun syncDirectTracks(
        userId: UserId,
        accessToken: AccessToken,
        trackIds: List<String>,
        previouslySynced: Int,
    ): Either<DomainError, Int> {
        if (trackIds.isEmpty()) return previouslySynced.right()
        return spotifyCatalog.getTracks(userId, accessToken, trackIds).flatMap { results ->
            if (results.isNotEmpty()) {
                appTrackRepository.upsertAll(results.map { it.track })
                appAlbumRepository.upsertAll(results.map { it.album })
                syncPoolRepository.removeTracks(results.map { it.track.id.value })
                val artistIds = results
                    .flatMap { r -> (listOf(r.track.artistId) + r.track.additionalArtistIds).map { it.value } }
                    .filter { it.isNotBlank() }.distinct()
                if (artistIds.isNotEmpty()) syncPoolRepository.addArtists(artistIds)
                logger.info { "Synced ${results.size} direct tracks; ${trackIds.size - results.size} not returned will be retried" }
            }
            (previouslySynced + results.size).right()
        }
    }

    // --- Outbox Handlers ---

    override fun handle(event: DomainOutboxEvent.SyncArtistDetails): OutboxTaskResult =
        handleOutboxTask("SyncArtistDetails[artist=${event.artistId},user=${event.userId.value}]") {
            syncArtistDetails(event.artistId, event.userId)
        }

    override fun handle(event: DomainOutboxEvent.SyncTrackDetails): OutboxTaskResult =
        handleOutboxTask("SyncTrackDetails[track=${event.trackId},user=${event.userId.value}]") {
            syncTrackDetails(event.trackId, event.userId)
        }

    override fun handle(event: DomainOutboxEvent.SyncMissingArtists): OutboxTaskResult =
        handleOutboxTask("SyncMissingArtists") { syncMissingArtists() }

    override fun handle(event: DomainOutboxEvent.SyncMissingTracks): OutboxTaskResult =
        handleOutboxTask("SyncMissingTracks") { syncMissingTracks() }

    override fun handle(event: DomainOutboxEvent.ResyncCatalog): OutboxTaskResult =
        handleOutboxTask("ResyncCatalog") { resyncCatalog() }

    private fun handleOutboxTask(taskDescription: String, operation: () -> Either<DomainError, *>): OutboxTaskResult = try {
        when (val result = operation()) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> {
                    logger.warn { "Rate limited on $taskDescription, retry after ${error.retryAfter.seconds}s" }
                    OutboxTaskResult.RateLimited(error.retryAfter)
                }
                else -> {
                    logger.error { "Failed $taskDescription: ${error.code}" }
                    OutboxTaskResult.Failed("Failed $taskDescription: ${error.code}")
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in $taskDescription" }
        OutboxTaskResult.Failed("Unexpected error in $taskDescription: ${e.message}", e)
    }

    companion object : KLogging() {
        private const val BULK_LIMIT = 50
    }
}
