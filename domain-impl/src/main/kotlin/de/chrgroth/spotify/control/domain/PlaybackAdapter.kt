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
import de.chrgroth.spotify.control.domain.model.AppPlaybackItem
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus
import de.chrgroth.spotify.control.domain.model.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.RecentlyPartialPlayedItem
import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.PlaybackPort
import de.chrgroth.spotify.control.domain.port.out.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.CurrentlyPlayingRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.PlaybackStatePort
import de.chrgroth.spotify.control.domain.port.out.RecentlyPartialPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.RecentlyPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAlbumDetailsPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyArtistDetailsPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyCurrentlyPlayingPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyRecentlyPlayedPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyTrackDetailsPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught", "TooManyFunctions", "LargeClass")
class PlaybackAdapter(
    private val userRepository: UserRepositoryPort,
    private val spotifyAccessToken: SpotifyAccessTokenPort,
    private val spotifyCurrentlyPlaying: SpotifyCurrentlyPlayingPort,
    private val spotifyRecentlyPlayed: SpotifyRecentlyPlayedPort,
    private val spotifyArtistDetails: SpotifyArtistDetailsPort,
    private val spotifyTrackDetails: SpotifyTrackDetailsPort,
    private val spotifyAlbumDetails: SpotifyAlbumDetailsPort,
    private val currentlyPlayingRepository: CurrentlyPlayingRepositoryPort,
    private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort,
    private val recentlyPartialPlayedRepository: RecentlyPartialPlayedRepositoryPort,
    private val appPlaybackRepository: AppPlaybackRepositoryPort,
    private val appArtistRepository: AppArtistRepositoryPort,
    private val appTrackRepository: AppTrackRepositoryPort,
    private val appAlbumRepository: AppAlbumRepositoryPort,
    private val outboxPort: OutboxPort,
    private val dashboardRefresh: DashboardRefreshPort,
    private val playbackState: PlaybackStatePort,
    private val appEnrichmentService: AppEnrichmentService,
    @ConfigProperty(name = "app.playback.minimum-progress-seconds", defaultValue = "25")
    minimumProgressSeconds: Long,
) : PlaybackPort {

    private val minimumProgressMs = minimumProgressSeconds * MS_PER_SECOND

    // --- Currently Playing ---

    override fun enqueueFetchCurrentlyPlaying() {
        val users = userRepository.findAll()
        logger.info { "Scheduling currently playing fetch for ${users.size} user(s)" }
        users.forEach { user ->
            outboxPort.enqueue(DomainOutboxEvent.FetchCurrentlyPlaying(user.spotifyUserId))
        }
    }

    override fun fetchCurrentlyPlaying(userId: UserId): Either<DomainError, Unit> {
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        return spotifyCurrentlyPlaying.getCurrentlyPlaying(userId, accessToken).flatMap { item ->
            if (item != null && item.isPlaying) {
                playbackState.onPlaybackDetected()
            }
            if (item != null && !currentlyPlayingRepository.existsByUserAndTrackAndObservedMinute(item)) {
                logger.info { "Persisting currently playing item for user: ${userId.value}, track: ${item.trackId}" }
                currentlyPlayingRepository.save(item)
                dashboardRefresh.notifyUserPlaybackData(userId)
            }
            Unit.right()
        }
    }

    // --- Recently Played ---

    override fun enqueueFetchRecentlyPlayed() {
        val users = userRepository.findAll()
        logger.info { "Scheduling recently played fetch for ${users.size} user(s)" }
        users.forEach { user ->
            outboxPort.enqueue(DomainOutboxEvent.FetchRecentlyPlayed(user.spotifyUserId))
        }
    }

    override fun fetchRecentlyPlayed(userId: UserId): Either<DomainError, Unit> {
        val accessToken = spotifyAccessToken.getValidAccessToken(userId)
        val after = recentlyPlayedRepository.findMostRecentPlayedAt(userId)
        return spotifyRecentlyPlayed.getRecentlyPlayed(userId, accessToken, after).flatMap { tracks ->
            val playedAts = tracks.map { it.playedAt }.toSet()
            val existingPlayedAts = recentlyPlayedRepository.findExistingPlayedAts(userId, playedAts)
            val newItems = tracks.filter { it.playedAt !in existingPlayedAts }
            if (newItems.isNotEmpty()) {
                logger.info { "Persisting ${newItems.size} new recently played items for user: ${userId.value}" }
                recentlyPlayedRepository.saveAll(newItems)
            }
            val computedCount = convertPartialPlays(userId, tracks.map { it.trackId }.toSet())
            if (newItems.isNotEmpty() || computedCount > 0) {
                dashboardRefresh.notifyUserPlaybackData(userId)
                outboxPort.enqueue(DomainOutboxEvent.AppendPlaybackData(userId))
            }
            Unit.right()
        }
    }

    private fun convertPartialPlays(userId: UserId, completedTrackIds: Set<String>): Int {
        val allItems = currentlyPlayingRepository.findByUserId(userId)
        val sortedItems = allItems.sortedBy { it.observedAt }

        val sessions = buildSessions(sortedItems)

        val latestNonCompletedSession = sessions
            .filter { it.trackId !in completedTrackIds }
            .maxByOrNull { session -> session.items.maxOf { it.observedAt } }

        val convertibleSessions = sessions.filter { session ->
            session.trackId !in completedTrackIds
                && session !== latestNonCompletedSession
                && session.items.maxOf { it.progressMs } > minimumProgressMs
        }

        val newComputedCount = if (convertibleSessions.isNotEmpty()) {
            val partialItems = convertibleSessions.map { session ->
                val firstObservedAt = session.items.minOf { it.observedAt }
                val lastObservedAtForSession = session.items.maxOf { it.observedAt }
                val nextItem = sortedItems.firstOrNull { it.observedAt > lastObservedAtForSession && it.trackId != session.trackId }
                val playedMs = if (nextItem != null) {
                    (nextItem.observedAt - firstObservedAt).inWholeMilliseconds
                } else {
                    session.items.maxOf { it.progressMs }
                }
                val representative = session.items.maxBy { it.progressMs }
                RecentlyPartialPlayedItem(
                    spotifyUserId = userId,
                    trackId = session.trackId,
                    trackName = representative.trackName,
                    artistIds = representative.artistIds,
                    artistNames = representative.artistNames,
                    playedAt = firstObservedAt,
                    playedSeconds = playedMs / MS_PER_SECOND,
                )
            }
            val existingPlayedAts = recentlyPartialPlayedRepository.findExistingPlayedAts(userId, partialItems.map { it.playedAt }.toSet())
            val newPartial = partialItems.filter { it.playedAt !in existingPlayedAts }
            if (newPartial.isNotEmpty()) {
                logger.info { "Persisting ${newPartial.size} recently partial played items for user: ${userId.value}" }
                recentlyPartialPlayedRepository.saveAll(newPartial)
            }
            newPartial.size
        } else {
            0
        }

        val protectedTrackIds = latestNonCompletedSession?.let { setOf(it.trackId) } ?: emptySet()
        val convertedTrackIds = convertibleSessions.map { it.trackId }.filter { it !in protectedTrackIds }.toSet()
        currentlyPlayingRepository.deleteByUserIdAndTrackIds(userId, completedTrackIds + convertedTrackIds)
        return newComputedCount
    }

    private fun buildSessions(sortedItems: List<CurrentlyPlayingItem>): List<PlaySession> {
        val result = mutableListOf<PlaySession>()
        var lastTrackId: String? = null
        for (item in sortedItems) {
            if (lastTrackId == item.trackId) {
                result.last().items.add(item)
            } else {
                result.add(PlaySession(item.trackId, mutableListOf(item)))
            }
            lastTrackId = item.trackId
        }
        return result
    }

    private data class PlaySession(val trackId: String, val items: MutableList<CurrentlyPlayingItem>)

    // --- Playback Data ---

    override fun enqueueRebuildPlaybackData(userId: UserId) {
        logger.info { "Enqueuing playback data rebuild for user: ${userId.value}" }
        outboxPort.enqueue(DomainOutboxEvent.RebuildPlaybackData(userId))
    }

    override fun rebuildPlaybackData(userId: UserId) {
        logger.info { "Rebuilding playback data for user: ${userId.value}" }
        appPlaybackRepository.deleteAllByUserId(userId)
        appendPlaybackData(userId)
    }

    override fun appendPlaybackData(userId: UserId) {
        logger.info { "Appending playback data for user: ${userId.value}" }
        val since = appPlaybackRepository.findMostRecentPlayedAt(userId)
        val recentlyPlayed = recentlyPlayedRepository.findSince(userId, since)
        val partialPlayed = recentlyPartialPlayedRepository.findSince(userId, since)

        val inactiveArtistIds = appArtistRepository.findByPlaybackProcessingStatus(ArtistPlaybackProcessingStatus.INACTIVE)
            .map { it.artistId }
            .toSet()

        val filteredRecentlyPlayed = recentlyPlayed.filter { it.artistIds.firstOrNull() !in inactiveArtistIds }
        val filteredPartialPlayed = partialPlayed.filter { it.artistIds.firstOrNull() !in inactiveArtistIds }

        val allPlaybackItems = buildPlaybackItems(filteredRecentlyPlayed, filteredPartialPlayed)
        if (allPlaybackItems.isEmpty()) {
            logger.info { "No new playback items to append for user: ${userId.value}" }
            return
        }

        val existingPlayedAts = appPlaybackRepository.findExistingPlayedAts(
            userId = userId,
            playedAts = allPlaybackItems.map { it.playedAt }.toSet(),
        )
        val newPlaybackItems = allPlaybackItems.filter { it.playedAt !in existingPlayedAts }
        if (newPlaybackItems.isEmpty()) {
            logger.info { "All playback items already exist for user: ${userId.value}" }
            return
        }

        val artists = buildArtists(filteredRecentlyPlayed, filteredPartialPlayed)
        val tracks = buildTracks(filteredRecentlyPlayed, filteredPartialPlayed)

        logger.info { "Persisting ${newPlaybackItems.size} new app_playback items for user: ${userId.value}" }
        appPlaybackRepository.saveAll(newPlaybackItems)

        appEnrichmentService.upsertAndEnqueueEnrichment(artists, tracks, userId)
    }

    private fun buildPlaybackItems(
        recentlyPlayed: List<RecentlyPlayedItem>,
        partialPlayed: List<RecentlyPartialPlayedItem>,
    ) = recentlyPlayed.map { item ->
        AppPlaybackItem(
            userId = item.spotifyUserId,
            playedAt = item.playedAt,
            trackId = item.trackId,
            secondsPlayed = 0L, // Spotify recently played API does not include play duration
        )
    } + partialPlayed.map { item ->
        AppPlaybackItem(
            userId = item.spotifyUserId,
            playedAt = item.playedAt,
            trackId = item.trackId,
            secondsPlayed = item.playedSeconds,
        )
    }

    private fun buildArtists(
        recentlyPlayed: List<RecentlyPlayedItem>,
        partialPlayed: List<RecentlyPartialPlayedItem>,
    ) = (recentlyPlayed.flatMap { item ->
        item.artistIds.mapIndexedNotNull { index, artistId ->
            val name = item.artistNames.getOrNull(index) ?: return@mapIndexedNotNull null
            AppArtist(artistId = artistId, artistName = name)
        }
    } + partialPlayed.flatMap { item ->
        item.artistIds.mapIndexedNotNull { index, artistId ->
            val name = item.artistNames.getOrNull(index) ?: return@mapIndexedNotNull null
            AppArtist(artistId = artistId, artistName = name)
        }
    }).distinctBy { it.artistId }

    private fun buildTracks(
        recentlyPlayed: List<RecentlyPlayedItem>,
        partialPlayed: List<RecentlyPartialPlayedItem>,
    ) = (recentlyPlayed.mapNotNull { item ->
        val artistId = item.artistIds.firstOrNull() ?: return@mapNotNull null
        AppTrack(trackId = item.trackId, trackTitle = item.trackName, artistId = artistId, additionalArtistIds = item.artistIds.drop(1))
    } + partialPlayed.mapNotNull { item ->
        val artistId = item.artistIds.firstOrNull() ?: return@mapNotNull null
        AppTrack(trackId = item.trackId, trackTitle = item.trackName, artistId = artistId, additionalArtistIds = item.artistIds.drop(1))
    }).distinctBy { it.trackId }

    // --- Enrichment ---

    override fun enrichArtistDetails(artistId: String, userId: UserId): Either<DomainError, Unit> {
        val existing = appArtistRepository.findByArtistIds(setOf(artistId)).firstOrNull()
        if (existing?.lastEnrichmentDate != null && existing.artistName.isNotBlank()) {
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

    // --- Outbox Handlers ---

    override fun handle(event: DomainOutboxEvent.FetchCurrentlyPlaying): OutboxTaskResult = try {
        when (val result = fetchCurrentlyPlaying(event.userId)) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> {
                    logger.warn { "Rate limited on FetchCurrentlyPlaying for user ${event.userId.value}, retry after ${error.retryAfter.seconds}s" }
                    OutboxTaskResult.RateLimited(error.retryAfter)
                }
                else -> {
                    logger.error { "Failed to fetch currently playing for user ${event.userId.value}: ${error.code}" }
                    OutboxTaskResult.Failed("Failed to fetch currently playing: ${error.code}")
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(FetchCurrentlyPlaying) for user ${event.userId.value}" }
        OutboxTaskResult.Failed("Unexpected error in update: ${e.message}", e)
    }

    override fun handle(event: DomainOutboxEvent.FetchRecentlyPlayed): OutboxTaskResult = try {
        when (val result = fetchRecentlyPlayed(event.userId)) {
            is Either.Right -> OutboxTaskResult.Success
            is Either.Left -> when (val error = result.value) {
                is SpotifyRateLimitError -> {
                    logger.warn { "Rate limited on FetchRecentlyPlayed for user ${event.userId.value}, retry after ${error.retryAfter.seconds}s" }
                    OutboxTaskResult.RateLimited(error.retryAfter)
                }
                else -> {
                    logger.error { "Failed to fetch recently played for user ${event.userId.value}: ${error.code}" }
                    OutboxTaskResult.Failed("Failed to fetch recently played: ${error.code}")
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(FetchRecentlyPlayed) for user ${event.userId.value}" }
        OutboxTaskResult.Failed("Unexpected error in update: ${e.message}", e)
    }

    override fun handle(event: DomainOutboxEvent.RebuildPlaybackData): OutboxTaskResult = try {
        rebuildPlaybackData(event.userId)
        OutboxTaskResult.Success
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(RebuildPlaybackData) for user ${event.userId.value}" }
        OutboxTaskResult.Failed("Unexpected error in rebuild: ${e.message}", e)
    }

    override fun handle(event: DomainOutboxEvent.AppendPlaybackData): OutboxTaskResult = try {
        appendPlaybackData(event.userId)
        OutboxTaskResult.Success
    } catch (e: Exception) {
        logger.error(e) { "Unexpected error in handle(AppendPlaybackData) for user ${event.userId.value}" }
        OutboxTaskResult.Failed("Unexpected error in append: ${e.message}", e)
    }

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

    companion object : KLogging() {
        private const val MS_PER_SECOND = 1_000L
    }
}
