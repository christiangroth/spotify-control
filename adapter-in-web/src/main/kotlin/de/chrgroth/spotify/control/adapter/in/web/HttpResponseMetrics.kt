package de.chrgroth.spotify.control.adapter.`in`.web

import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class HttpResponseMetrics(
  @param:ConfigProperty(name = "app.web.slow-response-threshold-ms")
  private val slowResponseThresholdMs: Long,
) {

  fun <T> timed(operation: String, block: (Details) -> T): T {
    val details = Details()
    val startMs = System.currentTimeMillis()
    val result = block(details)
    val durationMs = System.currentTimeMillis() - startMs

    if (durationMs >= slowResponseThresholdMs) {
      val detailsSuffix = if (details.entries.isNotEmpty()) {
        " details=[${details.entries.joinToString(", ") { "${it.first}=${it.second}ms" }}]"
      } else {
        ""
      }
      logger.warn { "Slow HTTP response detected: operation=$operation duration=${durationMs}/${slowResponseThresholdMs}ms$detailsSuffix" }
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
