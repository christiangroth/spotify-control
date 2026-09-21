package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogBrowserPort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/following")
@ApplicationScoped
@Suppress("Unused")
class FollowingResource(
  private val catalogBrowser: CatalogBrowserPort,
  private val httpResponseMetrics: ResponseTimingPort,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun page(): TemplateInstance = httpResponseMetrics.timed("page.following.view") {
    Templates.following(catalogBrowser.getFollowedArtists())
  }
}
