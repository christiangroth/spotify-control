package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import java.util.logging.Handler
import java.util.logging.LogManager
import java.util.logging.LogRecord
import java.util.logging.Logger

@ApplicationScoped
class LogsCollector {

  private val logsBuffer = LogsBuffer()

  private val handler = object : Handler() {
    override fun publish(record: LogRecord?) {
      if (record != null) {
        logsBuffer.append(record)
      }
    }

    override fun flush() {
      // no-op
    }

    override fun close() {
      // no-op
    }
  }

  private var rootLogger: Logger? = null

  fun onStartup(@Observes @Suppress("UnusedParameter") event: StartupEvent) {
    rootLogger = LogManager.getLogManager().getLogger("")
    rootLogger?.addHandler(handler)
  }

  fun onShutdown(@Observes @Suppress("UnusedParameter") event: ShutdownEvent) {
    rootLogger?.removeHandler(handler)
  }

  fun getRecentLogs(): List<LogUiEntry> = logsBuffer.getRecent()
}
