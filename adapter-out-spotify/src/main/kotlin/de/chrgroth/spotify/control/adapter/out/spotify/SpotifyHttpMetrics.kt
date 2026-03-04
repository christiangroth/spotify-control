package de.chrgroth.spotify.control.adapter.out.spotify

import de.chrgroth.spotify.control.domain.model.OutgoingRequestStats
import de.chrgroth.spotify.control.domain.port.out.OutgoingRequestStatsPort
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import java.net.URI
import java.net.http.HttpResponse
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit

@ApplicationScoped
class SpotifyHttpMetrics(
    private val meterRegistry: MeterRegistry,
) : OutgoingRequestStatsPort {

    private val timers = ConcurrentHashMap<String, Timer>()
    private val requestTimestamps = ConcurrentHashMap<String, ConcurrentLinkedDeque<Instant>>()

    @PostConstruct
    fun initialize() {
        Gauge.builder("spotify.request.hosts", requestTimestamps) { it.size.toDouble() }
            .description("Number of tracked Spotify API hosts with recorded requests")
            .register(meterRegistry)
    }

    fun <T> timed(uri: URI, block: () -> HttpResponse<T>): HttpResponse<T> {
        val startMs = System.currentTimeMillis()
        val response = block()
        val durationMs = System.currentTimeMillis() - startMs
        record(uri, response.statusCode(), durationMs)
        return response
    }

    private fun record(uri: URI, statusCode: Int, durationMs: Long) {
        val url = "${uri.host}${uri.path}"
        timers.getOrPut("${url}_${statusCode}") {
            Timer.builder("spotify.request")
                .tag("url", url)
                .tag("status", statusCode.toString())
                .register(meterRegistry)
        }.record(durationMs, TimeUnit.MILLISECONDS)

        val host = uri.host
        val deque = requestTimestamps.getOrPut(host) { ConcurrentLinkedDeque() }
        deque.add(Instant.now())
        pruneOldEntries(deque)
    }

    override fun getRequestStats(): List<OutgoingRequestStats> {
        val cutoff = Instant.now().minusSeconds(WINDOW_SECONDS)
        return requestTimestamps.entries
            .map { (host, deque) ->
                pruneOldEntries(deque)
                OutgoingRequestStats(
                    host = host,
                    requestCountLast24h = deque.count { it.isAfter(cutoff) }.toLong(),
                )
            }
            .sortedBy { it.host }
    }

    private fun pruneOldEntries(deque: ConcurrentLinkedDeque<Instant>) {
        val cutoff = Instant.now().minusSeconds(WINDOW_SECONDS)
        while (deque.peekFirst()?.isBefore(cutoff) == true) {
            deque.pollFirst()
        }
    }

    companion object {
        private const val WINDOW_SECONDS = 24L * 60L * 60L
    }
}
