package de.chrgroth.spotify.control.domain.playlist

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.catalog.release.ReleaseClassifier
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.SingularityError
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistSyncStatus
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.model.playlist.SingularityChallengerGroup
import de.chrgroth.spotify.control.domain.model.playlist.SingularityTrackInfo
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.`in`.playlist.SingularityPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.SpotifyPlaylistPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.user.CurrentUserResolver
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import kotlin.time.Clock

/**
 * Both playlists are located per call instead of cached, since they are looked up via the small
 * [PlaylistRepositoryPort.findAll] list and change rarely (only on playlist type (re-)configuration).
 */
@ApplicationScoped
@Suppress("Unused", "TooManyFunctions")
class SingularityService(
  private val currentUserResolver: CurrentUserResolver,
  private val playlistRepository: PlaylistRepositoryPort,
  private val appTrackRepository: AppTrackRepositoryPort,
  private val appAlbumRepository: AppAlbumRepositoryPort,
  private val appArtistRepository: AppArtistRepositoryPort,
  private val spotifyPlaylist: SpotifyPlaylistPort,
  private val spotifyAccessToken: SpotifyAccessTokenPort,
  private val outboxPort: OutboxPort,
) : SingularityPort {

  override fun getChallengerGroups(): List<SingularityChallengerGroup> {
    currentUserResolver.userId() ?: return emptyList()
    val playlists = findEndAndStagingPlaylists() ?: return emptyList()
    val (endPlaylist, stagingPlaylist) = playlists

    val currentTrackByArtist = endPlaylist.tracks.associateBy { it.mainArtistId }
    val challengersByArtist = stagingPlaylist.tracks
      .filter { it.mainArtistId in currentTrackByArtist }
      .groupBy { it.mainArtistId }
    if (challengersByArtist.isEmpty()) return emptyList()

    val relevantArtistIds = challengersByArtist.keys
    val relevantTrackIds = challengersByArtist.values.flatten().map { it.trackId }.toSet() +
      relevantArtistIds.map { currentTrackByArtist.getValue(it).trackId }
    val trackById = appTrackRepository.findByTrackIds(relevantTrackIds).associateBy { it.id }
    val artistById = appArtistRepository.findByArtistIds(relevantArtistIds).associateBy { it.id }

    return challengersByArtist.map { (artistId, challengerTracks) ->
      val currentTrackId = currentTrackByArtist.getValue(artistId).trackId
      SingularityChallengerGroup(
        artistId = artistId.value,
        artistName = artistById[artistId]?.artistName ?: trackById[currentTrackId]?.artistName ?: artistId.value,
        currentTrack = toTrackInfo(currentTrackId, trackById),
        challengers = challengerTracks.map { toTrackInfo(it.trackId, trackById) }.sortedBy { it.title },
      )
    }.sortedBy { it.artistName }
  }

  override fun acceptChallenger(artistId: String, trackId: String): Either<DomainError, Unit> {
    currentUserResolver.userId() ?: return SingularityError.CHALLENGER_NOT_FOUND.left()
    if (!isOpenChallenger(ArtistId(artistId), TrackId(trackId))) return SingularityError.CHALLENGER_NOT_FOUND.left()
    logger.info { "Enqueueing accept for challenger $trackId (artist $artistId)" }
    outboxPort.enqueue(DomainOutboxEvent.AcceptSingularityChallenger(artistId, trackId))
    return Unit.right()
  }

  override fun discardChallenger(artistId: String, trackId: String): Either<DomainError, Unit> {
    currentUserResolver.userId() ?: return SingularityError.CHALLENGER_NOT_FOUND.left()
    if (!isOpenChallenger(ArtistId(artistId), TrackId(trackId))) return SingularityError.CHALLENGER_NOT_FOUND.left()
    logger.info { "Enqueueing discard for challenger $trackId (artist $artistId)" }
    outboxPort.enqueue(DomainOutboxEvent.DiscardSingularityChallenger(artistId, trackId))
    return Unit.right()
  }

  override fun handle(event: DomainOutboxEvent.AcceptSingularityChallenger): Either<DomainError, Unit> {
    currentUserResolver.userId() ?: return Unit.right()
    val artistId = ArtistId(event.artistId)
    val trackId = TrackId(event.trackId)
    val playlists = findEndAndStagingInfos() ?: return SingularityError.PLAYLISTS_NOT_CONFIGURED.left()
    val (endInfo, stagingInfo) = playlists
    val endPlaylist = playlistRepository.findByPlaylistId(endInfo.spotifyPlaylistId) ?: return SingularityError.PLAYLISTS_NOT_CONFIGURED.left()
    val accessToken = spotifyAccessToken.getValidAccessToken()

    val currentTrack = endPlaylist.tracks.withIndex().find { it.value.mainArtistId == artistId }
    val addOrReplaceResult = if (currentTrack != null) {
      spotifyPlaylist.replacePlaylistTrack(accessToken, endInfo.spotifyPlaylistId, currentTrack.value.trackId.value, trackId.value, currentTrack.index)
    } else {
      spotifyPlaylist.addPlaylistTracks(accessToken, endInfo.spotifyPlaylistId, listOf(trackId.value))
    }
    return addOrReplaceResult
      .flatMap { spotifyPlaylist.removePlaylistTracks(accessToken, stagingInfo.spotifyPlaylistId, listOf(trackId.value)) }
      .map {
        appArtistRepository.updateSingularityCurrentTrack(artistId, trackId, Clock.System.now())
        val trackTitle = appTrackRepository.findByTrackIds(setOf(trackId)).firstOrNull()?.title ?: trackId.value
        val artistName = appArtistRepository.findByArtistIds(setOf(artistId)).firstOrNull()?.artistName ?: artistId.value
        logger.info { "Accepted challenger '$trackTitle' by '$artistName' (${artistId.value}) onto '${PlaylistType.SINGULARITY_PLAYLIST_NAME}'" }
        outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(endInfo.spotifyPlaylistId))
        outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(stagingInfo.spotifyPlaylistId))
      }
  }

  override fun handle(event: DomainOutboxEvent.DiscardSingularityChallenger): Either<DomainError, Unit> {
    currentUserResolver.userId() ?: return Unit.right()
    val artistId = ArtistId(event.artistId)
    val trackId = TrackId(event.trackId)
    val stagingInfo = playlistRepository.findAll().find { it.type == PlaylistType.SINGULARITY_STAGING }
      ?: return SingularityError.PLAYLISTS_NOT_CONFIGURED.left()
    val accessToken = spotifyAccessToken.getValidAccessToken()
    return spotifyPlaylist.removePlaylistTracks(accessToken, stagingInfo.spotifyPlaylistId, listOf(trackId.value)).map {
      val trackTitle = appTrackRepository.findByTrackIds(setOf(trackId)).firstOrNull()?.title ?: trackId.value
      val artistName = appArtistRepository.findByArtistIds(setOf(artistId)).firstOrNull()?.artistName ?: artistId.value
      logger.info { "Discarded challenger '$trackTitle' by '$artistName' (${artistId.value}) from '${PlaylistType.SINGULARITY_STAGING_PLAYLIST_NAME}'" }
      outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(stagingInfo.spotifyPlaylistId))
    }
  }

  override fun handle(event: DomainOutboxEvent.ReconcileSingularityTracking): Either<DomainError, Unit> {
    currentUserResolver.userId() ?: return Unit.right()
    val allPlaylists = playlistRepository.findAll()
    val playlistInfo = allPlaylists.find { it.spotifyPlaylistId == event.playlistId } ?: return Unit.right()
    if (playlistInfo.type != PlaylistType.SINGULARITY && playlistInfo.type != PlaylistType.SINGULARITY_STAGING) {
      return Unit.right()
    }
    val endInfo = allPlaylists.find { it.type == PlaylistType.SINGULARITY } ?: return Unit.right()
    val stagingInfo = allPlaylists.find { it.type == PlaylistType.SINGULARITY_STAGING } ?: return Unit.right()
    val endPlaylist = playlistRepository.findByPlaylistId(endInfo.spotifyPlaylistId) ?: return Unit.right()
    val stagingPlaylist = playlistRepository.findByPlaylistId(stagingInfo.spotifyPlaylistId) ?: return Unit.right()

    val currentTrackByArtist = endPlaylist.tracks.associateBy { it.mainArtistId }
    if (currentTrackByArtist.isEmpty()) return Unit.right()
    val stagedTrackIdsByArtist = stagingPlaylist.tracks.groupBy({ it.mainArtistId }, { it.trackId })
    val artists = appArtistRepository.findByArtistIds(currentTrackByArtist.keys).associateBy { it.id }
    val now = Clock.System.now()

    currentTrackByArtist.forEach { (artistId, playlistTrack) ->
      val artist = artists[artistId] ?: return@forEach
      val currentTrackId = playlistTrack.trackId
      val knownTrackId = artist.singularityCurrentTrackId
      if (knownTrackId == null) {
        // First observation of this artist's current track since this field was introduced: learn it silently,
        // keeping any already-backfilled addedAt (see BackfillSingularityTrackingStarter) instead of overwriting it.
        appArtistRepository.updateSingularityCurrentTrack(artistId, currentTrackId, artist.singularityCurrentTrackAddedAt ?: now)
      } else if (knownTrackId != currentTrackId && currentTrackId !in stagedTrackIdsByArtist[artistId].orEmpty()) {
        logger.info { "Reconciled manual swap for artist '${artist.artistName}' (${artistId.value}) on '${PlaylistType.SINGULARITY_PLAYLIST_NAME}'" }
        appArtistRepository.updateSingularityCurrentTrack(artistId, currentTrackId, now)
      }
    }
    return Unit.right()
  }

  override fun handle(event: DomainOutboxEvent.DetectSingularityChallenger): Either<DomainError, Unit> {
    currentUserResolver.userId() ?: return Unit.right()
    val albumId = AlbumId(event.albumId)
    val album = appAlbumRepository.findByAlbumIds(setOf(albumId)).firstOrNull() ?: return Unit.right()
    val artistId = album.artistId ?: return Unit.right()
    val artist = appArtistRepository.findByArtistIds(setOf(artistId)).firstOrNull() ?: return Unit.right()
    if (artist.syncStatus != ArtistSyncStatus.SYNC || artist.singularityCurrentTrackId == null) return Unit.right()

    val reviewedUntil = artist.singularityReviewedUntil ?: artist.singularityCurrentTrackAddedAt ?: return Unit.right()
    val releaseInstant = ReleaseClassifier.parseReleaseDate(album.releaseDate) ?: return Unit.right()
    if (releaseInstant <= reviewedUntil) return Unit.right()

    val otherAlbums = appAlbumRepository.findByArtistId(artistId)
    if (ReleaseClassifier.isReissue(album, otherAlbums)) return Unit.right()

    val ownTracks = appTrackRepository.findByAlbumId(albumId).filter { it.artistId == artistId }
    if (ownTracks.isEmpty()) return Unit.right()
    val stagingInfo = playlistRepository.findAll().find { it.type == PlaylistType.SINGULARITY_STAGING }
      ?: return SingularityError.PLAYLISTS_NOT_CONFIGURED.left()

    val accessToken = spotifyAccessToken.getValidAccessToken()
    return spotifyPlaylist.addPlaylistTracks(accessToken, stagingInfo.spotifyPlaylistId, ownTracks.map { it.id.value }).map {
      appArtistRepository.advanceSingularityReviewedUntil(artistId, releaseInstant)
      logger.info { "Staged new release '${album.title ?: albumId.value}' (${albumId.value}) by '${artist.artistName}' (${artistId.value}) as challenger(s) on '${PlaylistType.SINGULARITY_STAGING_PLAYLIST_NAME}'" }
      outboxPort.enqueue(DomainOutboxEvent.SyncPlaylistData(stagingInfo.spotifyPlaylistId))
    }
  }

  private fun isOpenChallenger(artistId: ArtistId, trackId: TrackId): Boolean {
    val playlists = findEndAndStagingPlaylists() ?: return false
    val (endPlaylist, stagingPlaylist) = playlists
    val hasCurrentTrack = endPlaylist.tracks.any { it.mainArtistId == artistId }
    val isStaged = stagingPlaylist.tracks.any { it.mainArtistId == artistId && it.trackId == trackId }
    return hasCurrentTrack && isStaged
  }

  private fun findEndAndStagingInfos(): Pair<PlaylistInfo, PlaylistInfo>? {
    val playlists = playlistRepository.findAll()
    val endInfo = playlists.find { it.type == PlaylistType.SINGULARITY } ?: return null
    val stagingInfo = playlists.find { it.type == PlaylistType.SINGULARITY_STAGING } ?: return null
    return endInfo to stagingInfo
  }

  private fun findEndAndStagingPlaylists(): Pair<Playlist, Playlist>? {
    val (endInfo, stagingInfo) = findEndAndStagingInfos() ?: return null
    val endPlaylist = playlistRepository.findByPlaylistId(endInfo.spotifyPlaylistId) ?: return null
    val stagingPlaylist = playlistRepository.findByPlaylistId(stagingInfo.spotifyPlaylistId) ?: return null
    return endPlaylist to stagingPlaylist
  }

  private fun toTrackInfo(trackId: TrackId, trackById: Map<TrackId, AppTrack>): SingularityTrackInfo =
    SingularityTrackInfo(trackId = trackId.value, title = trackById[trackId]?.title ?: trackId.value)

  companion object : KLogging()
}
