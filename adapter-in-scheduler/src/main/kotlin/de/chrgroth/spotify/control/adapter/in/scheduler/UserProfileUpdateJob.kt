package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.outbox.AppOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class UserProfileUpdateJob(
    private val outboxPort: OutboxPort,
) {

    @Scheduled(cron = "0 0 4 * * ?")
    fun run() {
        logger.info { "Enqueuing user profile update" }
        outboxPort.enqueue(AppOutboxEvent.UpdateUserProfiles)
    }

    companion object : KLogging()
}
