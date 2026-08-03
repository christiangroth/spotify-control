package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckDashboardSummary
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckViolation
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistId
import de.chrgroth.spotify.control.domain.port.out.readmodel.PlaylistCheckDashboardRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

@ApplicationScoped
class PlaylistCheckDashboardRepositoryAdapter(
  private val playlistCheckDashboardDocumentRepository: PlaylistCheckDashboardDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : PlaylistCheckDashboardRepositoryPort {

  override fun save(summary: PlaylistCheckDashboardSummary) {
    val document = summary.toDocument()
    mongoQueryMetrics.timed("app_playlist_check_dashboard.save") {
      playlistCheckDashboardDocumentRepository.persistOrUpdate(document)
    }
  }

  override fun find(): PlaylistCheckDashboardSummary? =
    mongoQueryMetrics.timed("app_playlist_check_dashboard.find") {
      playlistCheckDashboardDocumentRepository.findById(SINGLETON_ID)?.toDomain()
    }

  private fun PlaylistCheckDashboardDocument.toDomain() = PlaylistCheckDashboardSummary(
    displayName = displayName,
    playlistNameById = playlistNameById,
    checks = checks.map { entry ->
      AppPlaylistCheck(
        checkId = entry.checkId,
        playlistId = PlaylistId(entry.playlistId),
        lastCheck = entry.lastCheck.toKotlinInstant(),
        succeeded = entry.succeeded,
        violations = entry.violations.map { violation -> PlaylistCheckViolation(id = violation.id, message = violation.message) },
      )
    },
  )

  private fun PlaylistCheckDashboardSummary.toDocument() = PlaylistCheckDashboardDocument().apply {
    id = SINGLETON_ID
    displayName = this@toDocument.displayName
    playlistNameById = this@toDocument.playlistNameById
    checks = this@toDocument.checks.map { check ->
      PlaylistCheckDashboardEntryDocument().apply {
        checkId = check.checkId
        playlistId = check.playlistId.value
        lastCheck = check.lastCheck.toJavaInstant()
        succeeded = check.succeeded
        violations = check.violations.map { violation ->
          PlaylistCheckViolationDocument().apply {
            id = violation.id
            message = violation.message
          }
        }
      }
    }
  }

  companion object {
    const val SINGLETON_ID = "singleton"
  }
}
