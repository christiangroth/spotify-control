package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.outbox.AppOutboxEvent
import de.chrgroth.spotify.control.domain.port.out.OutboxPort
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class RecentlyPlayedFetchJob(
    private val outboxPort: OutboxPort,
) {

    @Scheduled(cron = "0 0/15 * * * ?")
    fun run() {
        logger.info { "Enqueuing recently played fetch" }
        outboxPort.enqueue(AppOutboxEvent.FetchRecentlyPlayed)
    }

    companion object : KLogging()
}
