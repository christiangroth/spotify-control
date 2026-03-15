package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.quarkus.starters.Starter
import de.chrgroth.spotify.control.domain.port.out.AppSyncPoolRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class DeletePendingSyncMissingTasksStarter(
    private val syncPoolRepository: AppSyncPoolRepositoryPort,
) : Starter {

    override val id = "DeletePendingSyncMissingTasksStarter-v2"

    override fun execute() {
        syncPoolRepository.resetEnqueued()
        logger.info { "Reset enqueued flag for all sync pool items" }
    }

    companion object : KLogging()
}
