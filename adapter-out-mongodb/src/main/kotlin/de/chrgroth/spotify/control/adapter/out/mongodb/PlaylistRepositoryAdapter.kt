package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.Playlist
import de.chrgroth.spotify.control.domain.model.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.PlaylistType
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
class PlaylistRepositoryAdapter : PlaylistRepositoryPort {

    @Inject
    lateinit var playlistMetadataDocumentRepository: PlaylistMetadataDocumentRepository

    @Inject
    lateinit var playlistDocumentRepository: PlaylistDocumentRepository

    @Inject
    lateinit var mongoQueryMetrics: MongoQueryMetrics

    override fun findByUserId(userId: UserId): List<PlaylistInfo> =
        mongoQueryMetrics.timed("spotify_playlist_metadata.findByUserId") {
            playlistMetadataDocumentRepository
                .list("spotifyUserId = ?1", userId.value)
                .map { it.toDomain() }
        }

    override fun saveAll(userId: UserId, playlists: List<PlaylistInfo>) {
        logger.info { "Saving ${playlists.size} playlist metadata document(s) for user ${userId.value}" }
        mongoQueryMetrics.timed("spotify_playlist_metadata.deleteByUserId") {
            playlistMetadataDocumentRepository.delete("spotifyUserId = ?1", userId.value)
        }
        if (playlists.isNotEmpty()) {
            val documents = playlists.map { it.toDocument(userId) }
            mongoQueryMetrics.timed("spotify_playlist_metadata.saveAll") {
                playlistMetadataDocumentRepository.persist(documents)
            }
        }
    }

    override fun findByUserIdAndPlaylistId(userId: UserId, playlistId: String): Playlist? =
        mongoQueryMetrics.timed("spotify_playlist.findByUserIdAndPlaylistId") {
            playlistDocumentRepository.findById("${userId.value}:$playlistId")?.toDomain()
        }

    override fun save(userId: UserId, playlist: Playlist) {
        logger.info { "Saving playlist document for playlist ${playlist.spotifyPlaylistId} (user ${userId.value}) with ${playlist.tracks.size} track(s)" }
        val document = playlist.toDocument(userId)
        mongoQueryMetrics.timed("spotify_playlist.save") {
            playlistDocumentRepository.persistOrUpdate(document)
        }
    }

    override fun updateLastSyncTime(userId: UserId, playlistId: String, time: kotlin.time.Instant) {
        val id = "${userId.value}:$playlistId"
        mongoQueryMetrics.timed("spotify_playlist_metadata.updateLastSyncTime") {
            playlistMetadataDocumentRepository.findById(id)?.let { doc ->
                doc.lastSyncTime = time.toJavaInstant()
                playlistMetadataDocumentRepository.persistOrUpdate(doc)
            }
        }
    }

    override fun findArtistIdsInActivePlaylists(): Set<String> {
        val activeMetadata = mongoQueryMetrics.timed("spotify_playlist_metadata.findAllActive") {
            playlistMetadataDocumentRepository.list("syncStatus = ?1", PlaylistSyncStatus.ACTIVE.name)
        }
        return mongoQueryMetrics.timed("spotify_playlist.findArtistIdsInActivePlaylists") {
            activeMetadata
                .mapNotNull { meta -> playlistDocumentRepository.findById("${meta.spotifyUserId}:${meta.spotifyPlaylistId}") }
                .flatMap { playlist -> playlist.tracks.flatMap { it.artistIds } }
                .toSet()
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

    private fun PlaylistInfo.toDocument(userId: UserId) = PlaylistMetadataDocument().apply {
        id = "${userId.value}:${this@toDocument.spotifyPlaylistId}"
        spotifyUserId = userId.value
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
        snapshotId = snapshotId,
        tracks = tracks.map { it.toDomain() },
    )

    private fun PlaylistTrackSubdocument.toDomain() = PlaylistTrack(
        trackId = trackId,
        trackName = trackName,
        artistIds = artistIds,
        artistNames = artistNames,
    )

    private fun Playlist.toDocument(userId: UserId) = PlaylistDocument().apply {
        id = "${userId.value}:${this@toDocument.spotifyPlaylistId}"
        spotifyUserId = userId.value
        spotifyPlaylistId = this@toDocument.spotifyPlaylistId
        snapshotId = this@toDocument.snapshotId
        tracks = this@toDocument.tracks.map { it.toSubdocument() }
    }

    private fun PlaylistTrack.toSubdocument() = PlaylistTrackSubdocument().apply {
        trackId = this@toSubdocument.trackId
        trackName = this@toSubdocument.trackName
        artistIds = this@toSubdocument.artistIds
        artistNames = this@toSubdocument.artistNames
    }

    companion object : KLogging()
}

