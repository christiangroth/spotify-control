package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n.Language
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogBrowserPort
import io.quarkus.qute.EngineBuilder
import io.vertx.ext.web.RoutingContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class AppTemplateGlobals(
  private val catalogBrowser: CatalogBrowserPort,
) {

  @field:ConfigProperty(name = "quarkus.application.version")
  lateinit var version: String

  @field:ConfigProperty(name = "grafana.cloud.stack-url", defaultValue = "")
  lateinit var grafanaCloudStackUrl: String

  @Inject
  private lateinit var routingContext: RoutingContext

  fun onEngineBuilder(@Observes builder: EngineBuilder) {
    builder.addTemplateInstanceInitializer { instance ->
      instance.data("appBuildVersion", version)
      instance.data("grafanaCloudStackUrl", grafanaCloudStackUrl)
      instance.data("undecidedArtistCount", catalogBrowser.getCatalogStats().undecidedArtistCount)
      val language = currentLanguage()
      instance.setLocale(language.locale)
      instance.data("currentLanguage", language.code)
    }
  }

  private fun currentLanguage(): Language {
    val cookieValue = runCatching { routingContext.request().getCookie(Language.COOKIE_NAME)?.value }.getOrNull()
    return Language.fromCode(cookieValue)
  }
}
