package de.chrgroth.spotify.control.domain

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import de.chrgroth.outbox.OutboxTaskResult
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.SpotifyRateLimitError
import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.model.AppPlaybackItem
import de.chrgroth.spotify.control.domain.model.AppTrack
import de.chrgroth.spotify.control.domain.model.ArtistId
import de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus
import de.chrgroth.spotify.control.domain.model.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.RecentlyPartialPlayedItem
import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.CatalogPort
import de.chrgroth.spotify.control.domain.port.`in`.PlaybackPort
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppPlaybackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.CurrentlyPlayingRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.DashboardRefreshPort
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.PlaybackStatePort
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.RecentlyPartialPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.RecentlyPlayedRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.SpotifyPlaybackPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class PlaybackAdapter(
    private val userRepository: UserRepositoryPort,
    private val spotifyAccessToken: SpotifyAccessTokenPort,
    private val spotifyPlayback: SpotifyPlaybackPort,
    private val currentlyPlayingRepository: CurrentlyPlayingRepositoryPort,
    private val recentlyPlayedRepository: RecentlyPlayedRepositoryPort,
    private val recentlyPartialPlayedRepository: RecentlyPartialPlayedRepositoryPort,
    private val appPlaybackRepository: AppPlaybackRepositoryPort,
    private val appArtistRepository: AppArtistRepositoryPort,
    private val outboxPort: OutboxPort,
    private val dashboardRefresh: DashboardRefreshPort,
    private val playbackState: PlaybackStatePort,
    private val appSyncService: AppSyncService,
    private val catalog: CatalogPort,
    private val playlistRepository: PlaylistRepositoryPort,
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
        return spotifyPlayback.getCurrentlyPlaying(userId, accessToken).flatMap { item ->
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
      return spotifyPlayback.getRecentlyPlayed(userId, accessToken, after).flatMap { tracks ->
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

    // Group items into contiguous play sessions per track.
    // A new session for a track begins whenever a different track is observed between observations of the same track.
    val sessions = buildSessions(sortedItems)

    // The most recently observed non-completed session is protected — it may still be active
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

    // Only delete track entries that are not held by a protected session
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

        appSyncService.upsertAndAddToSyncPool(artists, tracks, userId)
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
        AppTrack(id = TrackId(item.trackId), title = item.trackName, artistId = ArtistId(artistId), additionalArtistIds = item.artistIds.drop(1).map { ArtistId(it) })
    } + partialPlayed.mapNotNull { item ->
        val artistId = item.artistIds.firstOrNull() ?: return@mapNotNull null
        AppTrack(id = TrackId(item.trackId), title = item.trackName, artistId = ArtistId(artistId), additionalArtistIds = item.artistIds.drop(1).map { ArtistId(it) })
    }).distinctBy { it.id }

    // --- Artist Playback Sync ---

    override fun syncArtistPlaybackFromPlaylists(userId: UserId) {
        val activePlaylistArtistIds = playlistRepository.findArtistIdsInActivePlaylists()
        logger.info { "Found ${activePlaylistArtistIds.size} artist(s) in active playlists" }

        ArtistPlaybackProcessingStatus.entries.forEach { currentStatus ->
            val artists = appArtistRepository.findByPlaybackProcessingStatus(currentStatus)
            artists.forEach { artist ->
                val inActivePlaylist = artist.artistId in activePlaylistArtistIds
                val newStatus = when (currentStatus) {
                    ArtistPlaybackProcessingStatus.UNDECIDED ->
                        if (inActivePlaylist) ArtistPlaybackProcessingStatus.ACTIVE else ArtistPlaybackProcessingStatus.INACTIVE
                    ArtistPlaybackProcessingStatus.ACTIVE ->
                        if (!inActivePlaylist) ArtistPlaybackProcessingStatus.INACTIVE else null
                    ArtistPlaybackProcessingStatus.INACTIVE ->
                        if (inActivePlaylist) ArtistPlaybackProcessingStatus.ACTIVE else null
                }
                if (newStatus != null) {
                    logger.info { "Sync from playlists: updating artist ${artist.artistId} from $currentStatus to $newStatus" }
                    catalog.updateArtistPlaybackProcessingStatus(artist.artistId, newStatus, userId)
                }
            }
        }
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

    companion object : KLogging() {
        private const val MS_PER_SECOND = 1_000L
    }
}
