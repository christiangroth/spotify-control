package de.chrgroth.spotify.control.adapter.`in`.web

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@ApplicationScoped
class HttpResponseMetrics(
  private val meterRegistry: MeterRegistry,
  @param:ConfigProperty(name = "app.web.slow-response-threshold-ms")
  private val slowResponseThresholdMs: Long,
) {

  private val timers = ConcurrentHashMap<String, Timer>()
  private val slowResponseCounters = ConcurrentHashMap<String, Counter>()

  fun <T> timed(operation: String, block: (Details) -> T): T {
    val details = Details()
    val startMs = System.currentTimeMillis()
    val result = block(details)
    val durationMs = System.currentTimeMillis() - startMs

    timers.getOrPut(operation) {
      Timer.builder("http.response")
        .tag("operation", operation)
        .publishPercentileHistogram()
        .register(meterRegistry)
    }.record(durationMs, TimeUnit.MILLISECONDS)

    if (durationMs >= slowResponseThresholdMs) {
      val detailsSuffix = if (details.entries.isNotEmpty()) {
        " details=[${details.entries.joinToString(", ") { "${it.first}=${it.second}ms" }}]"
      } else {
        ""
      }
      logger.warn { "Slow HTTP response detected: operation=$operation duration=${durationMs}/${slowResponseThresholdMs}ms$detailsSuffix" }

      slowResponseCounters.getOrPut(operation) {
        meterRegistry.counter("http.response.slow", "operation", operation)
      }.increment()
    }

    return result
  }

  class Details {
    val entries = mutableListOf<Pair<String, Long>>()

    fun <T> detail(name: String, block: () -> T): T {
      val startMs = System.currentTimeMillis()
      val result = block()
      val durationMs = System.currentTimeMillis() - startMs
      entries.add(name to durationMs)
      return result
    }

    suspend fun <T> detailSuspend(name: String, block: suspend () -> T): T {
      val startMs = System.currentTimeMillis()
      val result = block()
      val durationMs = System.currentTimeMillis() - startMs
      entries.add(name to durationMs)
      return result
    }
  }

  companion object : KLogging()
}
