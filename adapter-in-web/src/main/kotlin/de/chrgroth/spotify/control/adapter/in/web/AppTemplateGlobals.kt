package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogBrowserPort
import io.quarkus.qute.EngineBuilder
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class AppTemplateGlobals(
  private val catalogBrowser: CatalogBrowserPort,
) {

  @field:ConfigProperty(name = "quarkus.application.version")
  lateinit var version: String

  fun onEngineBuilder(@Observes builder: EngineBuilder) {
    builder.addTemplateInstanceInitializer { instance ->
      instance.data("appBuildVersion", version)
      instance.data("undecidedArtistCount", catalogBrowser.getCatalogStats().undecidedArtistCount)
    }
  }
}
