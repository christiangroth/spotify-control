package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistId
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

@ApplicationScoped
class AppPlaylistCheckRepositoryAdapter(
  private val appPlaylistCheckDocumentRepository: AppPlaylistCheckDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : AppPlaylistCheckRepositoryPort {

  override fun save(check: AppPlaylistCheck) {
    val document = check.toDocument()
    mongoQueryMetrics.timed("app_playlist_check.save") {
      appPlaylistCheckDocumentRepository.persistOrUpdate(document)
    }
  }

  override fun findByCheckId(checkId: String): AppPlaylistCheck? =
    mongoQueryMetrics.timed("app_playlist_check.findByCheckId") {
      appPlaylistCheckDocumentRepository.findById(checkId)?.toDomain()
    }

  override fun findAll(): List<AppPlaylistCheck> =
    mongoQueryMetrics.timed("app_playlist_check.findAll") {
      appPlaylistCheckDocumentRepository.listAll().map { it.toDomain() }
    }

  override fun countAll(): Long =
    mongoQueryMetrics.timed("app_playlist_check.countAll") {
      appPlaylistCheckDocumentRepository.count()
    }

  override fun countSucceeded(): Long =
    mongoQueryMetrics.timed("app_playlist_check.countSucceeded") {
      appPlaylistCheckDocumentRepository.count("succeeded = ?1", true)
    }

  override fun deleteByPlaylistId(playlistId: String) {
    mongoQueryMetrics.timed("app_playlist_check.deleteByPlaylistId") {
      appPlaylistCheckDocumentRepository.delete("playlistId = ?1", playlistId)
    }
  }

  override fun deleteAll() {
    mongoQueryMetrics.timed("app_playlist_check.deleteAll") {
      appPlaylistCheckDocumentRepository.deleteAll()
    }
  }

  private fun AppPlaylistCheckDocument.toDomain() = AppPlaylistCheck(
    checkId = checkId,
    playlistId = PlaylistId(playlistId),
    lastCheck = lastCheck.toKotlinInstant(),
    succeeded = succeeded,
    violations = violations,
  )

  private fun AppPlaylistCheck.toDocument() = AppPlaylistCheckDocument().apply {
    checkId = this@toDocument.checkId
    playlistId = this@toDocument.playlistId.value
    lastCheck = this@toDocument.lastCheck.toJavaInstant()
    succeeded = this@toDocument.succeeded
    violations = this@toDocument.violations
  }

}
