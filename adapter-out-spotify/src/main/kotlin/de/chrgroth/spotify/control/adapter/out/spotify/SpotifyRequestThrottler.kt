package de.chrgroth.spotify.control.adapter.out.spotify

import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.SpotifyThrottlingPort
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@ApplicationScoped
class SpotifyRequestThrottler : SpotifyThrottlingPort {

    private val lastRequestTimeByPartition = ConcurrentHashMap<String, Long>()
    private val throttleIntervalMs = AtomicLong(DEFAULT_THROTTLE_INTERVAL_MS)

    fun throttle(partitionKey: String) {
        if (partitionKey != DomainOutboxPartition.ToSpotify.key) return
        val lastTime = lastRequestTimeByPartition[partitionKey] ?: 0L
        val elapsed = System.currentTimeMillis() - lastTime
        val remaining = throttleIntervalMs.get() - elapsed
        if (remaining > 0) {
            Thread.sleep(remaining)
        }
        lastRequestTimeByPartition[partitionKey] = System.currentTimeMillis()
    }

    override fun getDefaultThrottleIntervalMs(): Long = DEFAULT_THROTTLE_INTERVAL_MS

    override fun getThrottleIntervalMs(): Long = throttleIntervalMs.get()

    override fun setThrottleIntervalMs(ms: Long) {
        throttleIntervalMs.set(ms)
    }

    companion object {
        const val DEFAULT_THROTTLE_INTERVAL_MS = 10000L
    }
}
