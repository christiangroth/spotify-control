package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.Playlist
import de.chrgroth.spotify.control.domain.model.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.PlaylistDataRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import mu.KLogging

@ApplicationScoped
class PlaylistDataRepositoryAdapter : PlaylistDataRepositoryPort {

    @Inject
    lateinit var playlistDocumentRepository: PlaylistDocumentRepository

    @Inject
    lateinit var mongoQueryMetrics: MongoQueryMetrics

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
