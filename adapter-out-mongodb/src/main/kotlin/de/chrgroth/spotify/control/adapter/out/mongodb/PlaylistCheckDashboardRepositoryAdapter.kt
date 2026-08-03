package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.adapter.out.mongodb.MongoQueryMetrics.Companion.SINGLETON_ID
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
    mongoQueryMetrics.saveSingleton(playlistCheckDashboardDocumentRepository, "app_playlist_check_dashboard", summary.toDocument())
  }

  override fun find(): PlaylistCheckDashboardSummary? =
    mongoQueryMetrics.findSingleton(playlistCheckDashboardDocumentRepository, "app_playlist_check_dashboard")?.toDomain()

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
}
