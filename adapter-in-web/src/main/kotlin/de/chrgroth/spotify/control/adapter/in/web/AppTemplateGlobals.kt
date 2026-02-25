package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.qute.TemplateGlobal
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.eclipse.microprofile.config.inject.ConfigProperty

@TemplateGlobal
class AppTemplateGlobals {

  companion object {
    @JvmField
    var appBuildVersion: String = ""
  }
}

@ApplicationScoped
@Suppress("Unused")
class AppTemplateGlobalsInitializer {

  @field:ConfigProperty(name = "app.build.version")
  lateinit var version: String

  fun onStart(@Observes event: StartupEvent) {
    AppTemplateGlobals.appBuildVersion = version
  }
}
