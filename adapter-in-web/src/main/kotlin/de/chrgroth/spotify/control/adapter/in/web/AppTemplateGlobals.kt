package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.qute.TemplateGlobal
import org.eclipse.microprofile.config.ConfigProvider

@TemplateGlobal
object AppTemplateGlobals {

  @JvmStatic
  fun appBuildVersion(): String =
    ConfigProvider.getConfig().getValue("app.build.version", String::class.java)
}
