package de.chrgroth.spotify.control.adapter.out.spotify

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class SpotifyRequestThrottler {

    private val lastRequestTimeByPartition = ConcurrentHashMap<String, Long>()

    fun throttle(partitionKey: String) {
        if (partitionKey != DomainOutboxPartition.ToSpotify.key) return
        val lastTime = lastRequestTimeByPartition[partitionKey] ?: 0L
        val elapsed = System.currentTimeMillis() - lastTime
        val remaining = THROTTLE_INTERVAL_MS - elapsed
        if (remaining > 0) {
            Thread.sleep(remaining)
        }
        lastRequestTimeByPartition[partitionKey] = System.currentTimeMillis()
    }

    companion object {
        private const val THROTTLE_INTERVAL_MS = 2000L
    }
}
