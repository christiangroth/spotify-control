package de.chrgroth.spotify.control.domain.catalog

import de.chrgroth.spotify.control.domain.infra.CatalogStatsCache
import de.chrgroth.spotify.control.domain.model.catalog.AlbumBrowseItem
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.ArtistBrowseItem
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistSyncStatus
import de.chrgroth.spotify.control.domain.model.catalog.CatalogStats
import de.chrgroth.spotify.control.domain.model.catalog.CatalogSyncEntityType
import de.chrgroth.spotify.control.domain.model.catalog.CatalogSyncTimelineEntry
import de.chrgroth.spotify.control.domain.model.catalog.CatalogSyncTimelinePage
import de.chrgroth.spotify.control.domain.model.catalog.SyncCause
import de.chrgroth.spotify.control.domain.model.catalog.SyncTraceDisplay
import de.chrgroth.spotify.control.domain.model.catalog.SyncTraceEntityType
import de.chrgroth.spotify.control.domain.model.catalog.TrackBrowseItem
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogBrowserPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.SyncTraceRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.user.CurrentUserResolver
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class CatalogBrowserService(
  private val appArtistRepository: AppArtistRepositoryPort,
  private val appAlbumRepository: AppAlbumRepositoryPort,
  private val appTrackRepository: AppTrackRepositoryPort,
  private val syncTraceRepository: SyncTraceRepositoryPort,
  private val playlistRepository: PlaylistRepositoryPort,
  private val currentUserResolver: CurrentUserResolver,
  private val catalogStatsCache: CatalogStatsCache,
) : CatalogBrowserPort {

  override fun getCatalogStats(): CatalogStats = catalogStatsCache.current()

  override fun getArtists(filter: String?): List<ArtistBrowseItem> {
    val filterTrimmed = filter?.trim()
    if (filterTrimmed.isNullOrBlank()) return emptyList()

    return toBrowseItems(appArtistRepository.searchByName(filterTrimmed, SEARCH_RESULT_LIMIT))
  }

  override fun getUndecidedArtists(): List<ArtistBrowseItem> =
    toBrowseItems(appArtistRepository.findByStatuses(ASSUMPTION_STATUSES))

  private fun toBrowseItems(artists: List<AppArtist>): List<ArtistBrowseItem> {
    val artistIds = artists.map { it.id }.toSet()
    val albumCountByArtistId = appAlbumRepository.findByArtistIds(artistIds).groupingBy { it.artistId?.value }.eachCount()
    val trackCountByArtistId = appTrackRepository.findByArtistIds(artistIds).groupingBy { it.artistId.value }.eachCount()

    return artists
      .sortedBy { it.artistName.lowercase() }
      .map { artist ->
        ArtistBrowseItem(
          artistId = artist.id.value,
          artistName = artist.artistName,
          imageLink = artist.imageLink,
          albumCount = albumCountByArtistId[artist.id.value] ?: 0,
          trackCount = trackCountByArtistId[artist.id.value] ?: 0,
          syncStatus = artist.syncStatus,
        )
      }
  }

  override fun getAlbums(filter: String?): List<AlbumBrowseItem> {
    val filterTrimmed = filter?.trim()
    if (filterTrimmed.isNullOrBlank()) return emptyList()

    val albums = appAlbumRepository.searchByTitle(filterTrimmed, SEARCH_RESULT_LIMIT)
    val albumIds = albums.map { it.id }.toSet()
    val tracksByAlbumId = appTrackRepository.findByAlbumIds(albumIds).groupBy { it.albumId?.value }

    return albums
      .sortedBy { it.title?.lowercase() }
      .map { album ->
        val albumTracks = tracksByAlbumId[album.id.value] ?: emptyList()
        AlbumBrowseItem(
          albumId = album.id.value,
          title = album.title,
          imageLink = album.imageLink,
          releaseDate = album.releaseDate,
          trackCount = albumTracks.size,
          durationMs = albumTracks.sumOf { it.durationMs ?: 0L },
          artistName = album.artistName,
        )
      }
  }

  override fun getArtistAlbums(artistId: String): List<AlbumBrowseItem> {
    val albums = appAlbumRepository.findByArtistId(ArtistId(artistId))
    val tracks = appTrackRepository.findByArtistId(ArtistId(artistId))
    val tracksByAlbumId = tracks.groupBy { it.albumId?.value }

    return albums
      .sortedWith(compareByDescending { it.releaseDate })
      .map { album ->
        val albumTracks = tracksByAlbumId[album.id.value] ?: emptyList()
        val totalDurationMs = albumTracks.sumOf { it.durationMs ?: 0L }
        AlbumBrowseItem(
          albumId = album.id.value,
          title = album.title,
          imageLink = album.imageLink,
          releaseDate = album.releaseDate,
          trackCount = albumTracks.size,
          durationMs = totalDurationMs,
        )
      }
  }

  override fun getAlbumTracks(albumId: String): List<TrackBrowseItem> {
    val tracks = appTrackRepository.findByAlbumId(AlbumId(albumId))
    return tracks
      .sortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }))
      .map { track ->
        TrackBrowseItem(
          trackId = track.id.value,
          trackNumber = track.trackNumber,
          discNumber = track.discNumber,
          title = track.title,
          durationMs = track.durationMs ?: 0L,
        )
      }
  }

  override fun getArtistSyncTrace(artistId: String): SyncTraceDisplay? {
    val trace = syncTraceRepository.find(SyncTraceEntityType.ARTIST, artistId) ?: return null
    return SyncTraceDisplay(describeCause(trace.cause), trace.triggeredAt)
  }

  override fun getAlbumSyncTrace(albumId: String): SyncTraceDisplay? {
    val trace = syncTraceRepository.find(SyncTraceEntityType.ALBUM, albumId) ?: return null
    var description = describeCause(trace.cause)
    val cause = trace.cause
    if (cause is SyncCause.ArtistDiscography) {
      val artistTrace = syncTraceRepository.find(SyncTraceEntityType.ARTIST, cause.artistId)
      if (artistTrace != null) {
        description += " (artist itself was synced because: ${describeCause(artistTrace.cause)})"
      }
    }
    return SyncTraceDisplay(description, trace.triggeredAt)
  }

  override fun getSyncTimeline(artistOffset: Int, albumOffset: Int, limit: Int): CatalogSyncTimelinePage {
    val artistCandidates = appArtistRepository.findRecentlySynced(artistOffset, limit + 1)
    val albumCandidates = appAlbumRepository.findRecentlySynced(albumOffset, limit + 1)

    val artistHasMoreBeyondBatch = artistCandidates.size > limit
    val albumHasMoreBeyondBatch = albumCandidates.size > limit
    val boundedArtists = artistCandidates.take(limit)
    val boundedAlbums = albumCandidates.take(limit)

    val albumCountByArtistId = appAlbumRepository.countByArtistIds(boundedArtists.map { it.id }.toSet())

    val artistEntries = boundedArtists.map { artist ->
      CatalogSyncTimelineEntry(
        entityType = CatalogSyncEntityType.ARTIST,
        syncedAt = artist.lastSync,
        artistId = artist.id.value,
        artistName = artist.artistName,
        albumCount = (albumCountByArtistId[artist.id.value] ?: 0L).toInt(),
      )
    }
    val albumEntries = boundedAlbums.map { album ->
      CatalogSyncTimelineEntry(
        entityType = CatalogSyncEntityType.ALBUM,
        syncedAt = album.lastSync,
        artistId = album.artistId?.value,
        artistName = album.artistName,
        albumId = album.id.value,
        albumName = album.title,
        trackCount = album.totalTracks ?: 0,
      )
    }

    val merged = (artistEntries + albumEntries).sortedByDescending { it.syncedAt }
    val page = merged.take(limit)
    val artistTaken = page.count { it.entityType == CatalogSyncEntityType.ARTIST }
    val albumTaken = page.count { it.entityType == CatalogSyncEntityType.ALBUM }

    return CatalogSyncTimelinePage(
      entries = page,
      nextArtistOffset = artistOffset + artistTaken,
      nextAlbumOffset = albumOffset + albumTaken,
      hasMore = merged.size > limit || artistHasMoreBeyondBatch || albumHasMoreBeyondBatch,
    )
  }

  private fun describeCause(cause: SyncCause): String = when (cause) {
    is SyncCause.Playback -> "Played track '${trackName(cause.trackId)}'"
    is SyncCause.Playlist -> "Found in playlist '${playlistName(cause.playlistId)}' via track '${trackName(cause.trackId)}'"
    is SyncCause.ArtistDiscography -> "Synced as part of full discography sync for artist '${artistName(cause.artistId)}'"
    is SyncCause.ManualResync -> "Manual or scheduled catalog resync"
  }

  private fun trackName(trackId: String): String =
    appTrackRepository.findByTrackIds(setOf(TrackId(trackId))).firstOrNull()?.title ?: trackId

  private fun artistName(artistId: String): String =
    appArtistRepository.findByArtistIds(setOf(ArtistId(artistId))).firstOrNull()?.artistName ?: artistId

  private fun playlistName(playlistId: String): String {
    currentUserResolver.userId() ?: return playlistId
    return playlistRepository.findAll().firstOrNull { it.spotifyPlaylistId == playlistId }?.name ?: playlistId
  }

  companion object {
    internal const val SEARCH_RESULT_LIMIT = 50
    private val ASSUMPTION_STATUSES = setOf(ArtistSyncStatus.SYNC_ASSUMPTION, ArtistSyncStatus.SHALLOW_ASSUMPTION)
  }
}
