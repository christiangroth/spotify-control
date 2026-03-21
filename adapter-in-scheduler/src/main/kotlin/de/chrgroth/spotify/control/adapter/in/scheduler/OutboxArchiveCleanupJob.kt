package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.quarkus.outbox.domain.port.`in`.ArchiverPort
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Instant
import java.time.temporal.ChronoUnit

@ApplicationScoped
@Suppress("Unused")
class OutboxArchiveCleanupJob(
    private val archiverPort: ArchiverPort,
    @param:ConfigProperty(name = "outbox.archive.retention-days", defaultValue = "30")
    private val retentionDays: Long,
) {

    @Scheduled(cron = "0 0 1 * * ?")
    fun run() {
        val cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS)
        val deleted = archiverPort.deleteOlderThan(cutoff)
        logger.info { "Deleted $deleted archived outbox tasks older than $cutoff ($retentionDays days)" }
    }

    companion object : KLogging()
}
