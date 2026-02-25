package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.qute.TemplateGlobal
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class AppTemplateGlobals {

  @field:ConfigProperty(name = "app.build.version")
  lateinit var version: String

  @PostConstruct
  fun init() {
    appBuildVersion = version
  }

  companion object {
    @TemplateGlobal
    @JvmField
    var appBuildVersion: String = ""
  }
}
