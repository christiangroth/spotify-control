package de.chrgroth.spotify.control.adapter.`in`.http.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.eclipse.microprofile.config.inject.ConfigProperty

// registers eagerly on StartupEvent so this gauge is always visible, even before any user activity occurs
@ApplicationScoped
@Suppress("Unused", "UnusedParameter")
class ApplicationInfoMetrics(
  private val meterRegistry: MeterRegistry,
) {

  @field:ConfigProperty(name = "quarkus.application.version")
  lateinit var version: String

  fun onStartup(@Observes event: StartupEvent) {
    Gauge.builder("application.info", this) { 1.0 }
      .tag("version", version)
      .description("Static info metric carrying the running application version as a label")
      .register(meterRegistry)
  }
}
