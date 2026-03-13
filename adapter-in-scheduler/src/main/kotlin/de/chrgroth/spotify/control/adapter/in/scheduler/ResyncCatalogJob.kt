package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import de.chrgroth.quarkus.starters.StarterSkipPredicate
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class ResyncCatalogJob(
    private val outboxPort: OutboxPort,
) {

    // Runs every Sunday at 03:00
    @Scheduled(cron = "0 0 3 ? * SUN", skipExecutionIf = StarterSkipPredicate::class)
    fun run() {
        logger.info { "Enqueuing ResyncCatalog" }
        outboxPort.enqueue(DomainOutboxEvent.ResyncCatalog())
    }

    companion object : KLogging()
}
