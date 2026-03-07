package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.spotify.control.util.outbox.Outbox
import de.chrgroth.starters.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class ArchiveFailedOutboxTasksStarter(private val outbox: Outbox) : Starter {

    override val id = "ArchiveFailedOutboxTasksStarter-v1"

    override fun execute() {
        val archived = outbox.archiveFailedTasks()
        logger.info { "Archived $archived failed outbox tasks" }
    }

    companion object : KLogging()
}
