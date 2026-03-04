package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
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
    lateinit var mongoQueryMetrics: MongoQueryMetrics

    override fun findByUserId(userId: UserId): List<PlaylistInfo> =
        mongoQueryMetrics.timed("playlist_metadata.findByUserId") {
            playlistMetadataDocumentRepository
                .list("spotifyUserId = ?1", userId.value)
                .map { it.toDomain() }
        }

    override fun saveAll(userId: UserId, playlists: List<PlaylistInfo>) {
        logger.info { "Saving ${playlists.size} playlist metadata document(s) for user ${userId.value}" }
        mongoQueryMetrics.timed("playlist_metadata.deleteByUserId") {
            playlistMetadataDocumentRepository.delete("spotifyUserId = ?1", userId.value)
        }
        if (playlists.isNotEmpty()) {
            val documents = playlists.map { it.toDocument(userId) }
            mongoQueryMetrics.timed("playlist_metadata.saveAll") {
                playlistMetadataDocumentRepository.persist(documents)
            }
        }
    }

    private fun PlaylistMetadataDocument.toDomain() = PlaylistInfo(
        spotifyPlaylistId = spotifyPlaylistId,
        snapshotId = snapshotId,
        lastSnapshotIdSyncTime = lastSnapshotIdSyncTime.toKotlinInstant(),
        name = name,
        syncStatus = PlaylistSyncStatus.valueOf(syncStatus),
    )

    private fun PlaylistInfo.toDocument(userId: UserId) = PlaylistMetadataDocument().apply {
        id = "${userId.value}:${this@toDocument.spotifyPlaylistId}"
        spotifyUserId = userId.value
        spotifyPlaylistId = this@toDocument.spotifyPlaylistId
        snapshotId = this@toDocument.snapshotId
        lastSnapshotIdSyncTime = this@toDocument.lastSnapshotIdSyncTime.toJavaInstant()
        name = this@toDocument.name
        syncStatus = this@toDocument.syncStatus.name
    }

    companion object : KLogging()
}
