package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.port.out.AppPlaylistCheckRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
class AppPlaylistCheckRepositoryAdapter : AppPlaylistCheckRepositoryPort {

    @Inject
    lateinit var appPlaylistCheckDocumentRepository: AppPlaylistCheckDocumentRepository

    @Inject
    lateinit var mongoQueryMetrics: MongoQueryMetrics

    override fun save(check: AppPlaylistCheck) {
        logger.info { "Saving playlist check ${check.checkId} for playlist ${check.playlistId}" }
        val document = check.toDocument()
        mongoQueryMetrics.timed("app_playlist_check.save") {
            appPlaylistCheckDocumentRepository.persistOrUpdate(document)
        }
    }

    override fun findByCheckId(checkId: String): AppPlaylistCheck? =
        mongoQueryMetrics.timedWithFallback("app_playlist_check.findByCheckId", null) {
            appPlaylistCheckDocumentRepository.findById(checkId)?.toDomain()
        }

    override fun findAll(): List<AppPlaylistCheck> =
        mongoQueryMetrics.timedWithFallback("app_playlist_check.findAll", emptyList()) {
            appPlaylistCheckDocumentRepository.listAll().map { it.toDomain() }
        }

    override fun countAll(): Long =
        mongoQueryMetrics.timedWithFallback("app_playlist_check.countAll", 0L) {
            appPlaylistCheckDocumentRepository.count()
        }

    override fun countSucceeded(): Long =
        mongoQueryMetrics.timedWithFallback("app_playlist_check.countSucceeded", 0L) {
            appPlaylistCheckDocumentRepository.count("succeeded = ?1", true)
        }

    override fun deleteByPlaylistId(playlistId: String) {
        logger.info { "Deleting playlist check documents for playlist $playlistId" }
        mongoQueryMetrics.timed("app_playlist_check.deleteByPlaylistId") {
            appPlaylistCheckDocumentRepository.delete("playlistId = ?1", playlistId)
        }
    }

    override fun deleteAll() {
        logger.info { "Deleting all playlist check documents" }
        mongoQueryMetrics.timed("app_playlist_check.deleteAll") {
            appPlaylistCheckDocumentRepository.deleteAll()
        }
    }

    private fun AppPlaylistCheckDocument.toDomain() = AppPlaylistCheck(
        checkId = checkId,
        playlistId = playlistId,
        lastCheck = lastCheck.toKotlinInstant(),
        succeeded = succeeded,
        violations = violations,
    )

    private fun AppPlaylistCheck.toDocument() = AppPlaylistCheckDocument().apply {
        checkId = this@toDocument.checkId
        playlistId = this@toDocument.playlistId
        lastCheck = this@toDocument.lastCheck.toJavaInstant()
        succeeded = this@toDocument.succeeded
        violations = this@toDocument.violations
    }

    companion object : KLogging()
}
