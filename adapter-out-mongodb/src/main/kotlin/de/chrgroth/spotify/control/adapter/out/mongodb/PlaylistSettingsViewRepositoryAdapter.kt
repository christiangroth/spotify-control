package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSettingsEntry
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSettingsView
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.port.out.readmodel.PlaylistSettingsViewRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

@ApplicationScoped
class PlaylistSettingsViewRepositoryAdapter(
  private val playlistSettingsViewDocumentRepository: PlaylistSettingsViewDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : PlaylistSettingsViewRepositoryPort {

  override fun save(view: PlaylistSettingsView) {
    val document = view.toDocument()
    mongoQueryMetrics.timed("app_playlist_settings_view.save") {
      playlistSettingsViewDocumentRepository.persistOrUpdate(document)
    }
  }

  override fun find(): PlaylistSettingsView? =
    mongoQueryMetrics.timed("app_playlist_settings_view.find") {
      playlistSettingsViewDocumentRepository.findById(SINGLETON_ID)?.toDomain()
    }

  private fun PlaylistSettingsViewDocument.toDomain() = PlaylistSettingsView(
    entries = entries.map { entry ->
      PlaylistSettingsEntry(
        playlist = PlaylistInfo(
          spotifyPlaylistId = entry.spotifyPlaylistId,
          snapshotId = entry.snapshotId,
          lastSnapshotIdSyncTime = entry.lastSnapshotIdSyncTime.toKotlinInstant(),
          name = entry.name,
          syncStatus = PlaylistSyncStatus.valueOf(entry.syncStatus),
          type = entry.type?.let { PlaylistType.valueOf(it) },
          lastSyncTime = entry.lastSyncTime?.toKotlinInstant(),
        ),
        numberOfTracks = entry.numberOfTracks,
        numberOfArtists = entry.numberOfArtists,
        numberOfMissingArtists = entry.numberOfMissingArtists,
      )
    },
  )

  private fun PlaylistSettingsView.toDocument() = PlaylistSettingsViewDocument().apply {
    id = SINGLETON_ID
    entries = this@toDocument.entries.map { entry ->
      PlaylistSettingsEntryDocument().apply {
        spotifyPlaylistId = entry.playlist.spotifyPlaylistId
        snapshotId = entry.playlist.snapshotId
        lastSnapshotIdSyncTime = entry.playlist.lastSnapshotIdSyncTime.toJavaInstant()
        name = entry.playlist.name
        syncStatus = entry.playlist.syncStatus.name
        type = entry.playlist.type?.name
        lastSyncTime = entry.playlist.lastSyncTime?.toJavaInstant()
        numberOfTracks = entry.numberOfTracks
        numberOfArtists = entry.numberOfArtists
        numberOfMissingArtists = entry.numberOfMissingArtists
      }
    }
  }

  companion object {
    const val SINGLETON_ID = "singleton"
  }
}
