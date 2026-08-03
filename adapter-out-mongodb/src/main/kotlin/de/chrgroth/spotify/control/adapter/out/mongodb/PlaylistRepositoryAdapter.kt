package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Projections
import com.mongodb.client.model.Updates
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import mu.KLogging

import org.bson.Document

@ApplicationScoped
class PlaylistRepositoryAdapter(
  private val playlistMetadataDocumentRepository: PlaylistMetadataDocumentRepository,
  private val playlistDocumentRepository: PlaylistDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : PlaylistRepositoryPort {

  override fun findAll(): List<PlaylistInfo> =
    mongoQueryMetrics.timed("spotify_playlist_metadata.findAll") {
      playlistMetadataDocumentRepository
        .listAll()
        .map { it.toDomain() }
    }

  override fun replaceAll(playlists: List<PlaylistInfo>) {
    mongoQueryMetrics.timed("spotify_playlist_metadata.deleteAll") {
      playlistMetadataDocumentRepository.deleteAll()
    }
    if (playlists.isNotEmpty()) {
      val documents = playlists.map { it.toDocument() }
      mongoQueryMetrics.timed("spotify_playlist_metadata.saveAll") {
        playlistMetadataDocumentRepository.persist(documents)
      }
    }
  }

  override fun findByPlaylistId(playlistId: String): Playlist? =
    mongoQueryMetrics.timed("spotify_playlist.findByPlaylistId") {
      playlistDocumentRepository.findById(playlistId)?.toDomain()
    }

  override fun findTrackCounts(): Map<String, Int> {
    // $size errors out entirely (aborting the whole aggregation, for every playlist) if $tracks is missing
    // on any single document, so it is wrapped in $ifNull to default to an empty array first.
    val pipeline = listOf(
      Aggregates.project(
        Projections.fields(
          Projections.include("spotifyPlaylistId"),
          Projections.computed("trackCount", Document("\$size", Document("\$ifNull", listOf("\$tracks", emptyList<String>())))),
        ),
      ),
    )
    return mongoQueryMetrics.timed("spotify_playlist.findTrackCounts") {
      playlistDocumentRepository.mongoCollection()
        .aggregate(pipeline, Document::class.java)
        .associate { it.getString("spotifyPlaylistId") to it.getInteger("trackCount") }
    }
  }

  override fun findDistinctArtistIds(): Map<String, Set<ArtistId>> {
    // Only the main (first) artist of each track is considered: PlaylistService.syncPlaylistData() only ever
    // syncs a track's primary artist into the app catalog, so featured/additional artists would always show up
    // as "missing from catalog" if they were included here.
    // mainArtistId is denormalized onto each track subdocument at write time (see PlaylistTrack.mainArtistId /
    // appendTracks()/toSubdocument()), so this only has to project that stored field per track with a single
    // $project/$map, one output document per playlist - no $unwind/$group shuffle across the whole collection,
    // and no per-track $arrayElemAt computation. Deduping the per-track ids happens client-side via toSet(),
    // which is a cheap O(n) hash pass and far faster in practice than making MongoDB unwind and regroup every
    // track in every playlist.
    val pipeline = listOf(
      Aggregates.project(
        Projections.fields(
          Projections.include("spotifyPlaylistId"),
          Projections.computed(
            "mainArtistIds",
            Document(
              "\$map",
              Document("input", Document("\$ifNull", listOf("\$tracks", emptyList<Document>())))
                .append("as", "track")
                .append("in", "\$\$track.mainArtistId"),
            ),
          ),
        ),
      ),
    )
    return mongoQueryMetrics.timed("spotify_playlist.findDistinctArtistIds") {
      playlistDocumentRepository.mongoCollection()
        .aggregate(pipeline, Document::class.java)
        .associate { doc ->
          doc.getString("spotifyPlaylistId") to doc.getList("mainArtistIds", String::class.java).filterNotNull().map { artistId -> ArtistId(artistId) }.toSet()
        }
    }
  }

  override fun save(playlist: Playlist) {
    val document = playlist.toDocument()
    mongoQueryMetrics.timed("spotify_playlist.save") {
      playlistDocumentRepository.persistOrUpdate(document)
    }
  }

  override fun appendTracks(playlistId: String, tracks: List<PlaylistTrack>) {
    if (tracks.isEmpty()) return
    mongoQueryMetrics.timed("spotify_playlist.appendTracks") {
      val trackDocuments = tracks.map { track ->
        Document("trackId", track.trackId.value)
          .append("artistIds", track.artistIds.map { it.value })
          .append("albumId", track.albumId?.value)
          .append("mainArtistId", track.mainArtistId.value)
      }
      val result = playlistDocumentRepository.mongoCollection().updateOne(
        Filters.eq("_id", playlistId),
        Updates.pushEach("tracks", trackDocuments),
      )
      if (result.matchedCount == 0L) {
        logger.warn { "Playlist document not found for appending tracks: $playlistId" }
      }
    }
  }

  override fun updateLastSyncTime(playlistId: String, time: kotlin.time.Instant) {
    mongoQueryMetrics.timed("spotify_playlist_metadata.updateLastSyncTime") {
      playlistMetadataDocumentRepository.findById(playlistId)?.let { doc ->
        doc.lastSyncTime = time.toJavaInstant()
        playlistMetadataDocumentRepository.persistOrUpdate(doc)
      }
    }
  }

  private fun PlaylistMetadataDocument.toDomain() = PlaylistInfo(
    spotifyPlaylistId = spotifyPlaylistId,
    snapshotId = snapshotId,
    lastSnapshotIdSyncTime = lastSnapshotIdSyncTime.toKotlinInstant(),
    name = name,
    syncStatus = PlaylistSyncStatus.valueOf(syncStatus),
    type = type?.let { PlaylistType.valueOf(it) },
    lastSyncTime = lastSyncTime?.toKotlinInstant(),
  )

  private fun PlaylistInfo.toDocument() = PlaylistMetadataDocument().apply {
    id = this@toDocument.spotifyPlaylistId
    spotifyPlaylistId = this@toDocument.spotifyPlaylistId
    snapshotId = this@toDocument.snapshotId
    lastSnapshotIdSyncTime = this@toDocument.lastSnapshotIdSyncTime.toJavaInstant()
    name = this@toDocument.name
    syncStatus = this@toDocument.syncStatus.name
    type = this@toDocument.type?.name
    lastSyncTime = this@toDocument.lastSyncTime?.toJavaInstant()
  }

  private fun PlaylistDocument.toDomain() = Playlist(
    spotifyPlaylistId = spotifyPlaylistId,
    tracks = tracks.map { it.toDomain() },
  )

  private fun PlaylistTrackSubdocument.toDomain(): PlaylistTrack {
    val trackArtistIds = artistIds.map { ArtistId(it) }
    return PlaylistTrack(
      trackId = TrackId(trackId),
      artistIds = trackArtistIds,
      albumId = albumId?.let { AlbumId(it) },
      // mainArtistId may still be unset on documents persisted before it was backfilled (see
      // BackfillPlaylistMainArtistIdStarter); fall back to computing it from artistIds for those.
      mainArtistId = mainArtistId?.let { ArtistId(it) } ?: trackArtistIds.first(),
    )
  }

  private fun Playlist.toDocument() = PlaylistDocument().apply {
    id = this@toDocument.spotifyPlaylistId
    spotifyPlaylistId = this@toDocument.spotifyPlaylistId
    tracks = this@toDocument.tracks.map { it.toSubdocument() }
  }

  private fun PlaylistTrack.toSubdocument() = PlaylistTrackSubdocument().apply {
    trackId = this@toSubdocument.trackId.value
    artistIds = this@toSubdocument.artistIds.map { it.value }
    albumId = this@toSubdocument.albumId?.value
    mainArtistId = this@toSubdocument.mainArtistId.value
  }

  override fun setAllSyncInactive() {
    mongoQueryMetrics.timed("spotify_playlist_metadata.setAllSyncInactive") {
      playlistMetadataDocumentRepository.mongoCollection().updateMany(
        Document(),
        Updates.set(SYNC_STATUS_FIELD, PlaylistSyncStatus.PASSIVE.name),
      )
    }
  }

  companion object : KLogging() {
    internal const val SYNC_STATUS_FIELD = "syncStatus"
  }
}
