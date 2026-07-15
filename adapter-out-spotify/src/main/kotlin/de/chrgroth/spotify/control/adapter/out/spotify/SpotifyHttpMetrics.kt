package de.chrgroth.spotify.control.adapter.out.spotify

import de.chrgroth.spotify.control.domain.model.infra.OutgoingRequestStats
import de.chrgroth.spotify.control.domain.port.out.infra.OutgoingRequestStatsObserver
import de.chrgroth.spotify.control.domain.port.out.infra.OutgoingRequestStatsPort
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.enterprise.inject.Any
import jakarta.enterprise.inject.Instance
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit

// registers eagerly on StartupEvent so this gauge is always visible, even before any Spotify request occurs
@ApplicationScoped
class SpotifyHttpMetrics(
  private val meterRegistry: MeterRegistry,
  @param:Any private val requestStatsObservers: Instance<OutgoingRequestStatsObserver>,
) : OutgoingRequestStatsPort {

  private val timers = ConcurrentHashMap<String, Timer>()
  private val requestTimestamps = ConcurrentHashMap<String, ConcurrentLinkedDeque<Instant>>()

  fun onStartup(@Observes event: StartupEvent) {
    Gauge.builder("spotify.request.endpoints", requestTimestamps) { it.size.toDouble() }
      .description("Number of tracked Spotify API endpoints with recorded requests")
      .register(meterRegistry)
  }

  fun <T> timed(urlTemplate: String, block: () -> T): T {
    val startMs = System.currentTimeMillis()
    var statusCode = HTTP_OK
    return try {
      block()
    } catch (e: SpotifyNoContentException) {
      statusCode = HTTP_NO_CONTENT
      throw e
    } catch (e: SpotifyApiException) {
      statusCode = e.statusCode
      throw e
    } finally {
      record(urlTemplate, statusCode, System.currentTimeMillis() - startMs)
    }
  }

  private fun record(urlTemplate: String, statusCode: Int, durationMs: Long) {
    timers.getOrPut("${urlTemplate}_${statusCode}") {
      Timer.builder("spotify.request")
        .tag("url", urlTemplate)
        .tag("status", statusCode.toString())
        .publishPercentileHistogram()
        .register(meterRegistry)
    }.record(durationMs, TimeUnit.MILLISECONDS)

    val deque = requestTimestamps.getOrPut(urlTemplate) { ConcurrentLinkedDeque() }
    deque.add(Instant.now())
    pruneOldEntries(deque)
    requestStatsObservers.forEach { it.onRequestRecorded() }
  }

  override fun current(): List<OutgoingRequestStats> {
    val cutoff = Instant.now().minusSeconds(WINDOW_SECONDS)
    return requestTimestamps.entries
      .map { (endpoint, deque) ->
        pruneOldEntries(deque)
        OutgoingRequestStats(
          endpoint = endpoint.let { if (it.contains('/')) it.substring(it.indexOf('/')) else it },
          requestCountLast24h = deque.count { it.isAfter(cutoff) }.toLong(),
        )
      }
      .sortedBy { it.endpoint }
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
