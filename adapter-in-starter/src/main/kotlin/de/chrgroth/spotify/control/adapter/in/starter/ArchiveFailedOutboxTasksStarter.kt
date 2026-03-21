package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.quarkus.outbox.domain.port.`in`.ArchiverPort
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Instant
import java.time.temporal.ChronoUnit

@ApplicationScoped
@Suppress("Unused")
class ArchiveFailedOutboxTasksStarter(
    private val archiverPort: ArchiverPort,
    @param:ConfigProperty(name = "outbox.archive.retention-days", defaultValue = "30")
    private val retentionDays: Long,
) : Starter {

    override val id = "ArchiveFailedOutboxTasksStarter-v1"

    override fun execute() {
        val cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS)
        val deleted = archiverPort.deleteOlderThan(cutoff)
        logger.info { "Deleted $deleted archived outbox tasks older than $cutoff ($retentionDays days)" }
    }

    companion object : KLogging()
}
