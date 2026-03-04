package de.chrgroth.spotify.control.adapter.`in`.web.ui

import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response

@Path("/ui/docs")
@ApplicationScoped
@Suppress("Unused")
class DocsResource {

  @GET
  @Authenticated
  fun redirectToDocs(): Response = Response.seeOther(java.net.URI.create("/ui/docs/arc42/arc42.md")).build()

  @GET
  @Authenticated
  @Path("/arc42")
  fun redirectToArc42(): Response = Response.seeOther(java.net.URI.create("/ui/docs/arc42/arc42.md")).build()

  @GET
  @Authenticated
  @Path("/starters")
  fun redirectToStarters(): Response = Response.seeOther(java.net.URI.create("/ui/docs/arc42/starters.md")).build()

  @GET
  @Authenticated
  @Path("/outbox")
  fun redirectToOutbox(): Response = Response.seeOther(java.net.URI.create("/ui/docs/arc42/outbox.md")).build()

  @GET
  @Authenticated
  @Path("/releasenotes")
  fun redirectToReleasenotes(): Response = Response.seeOther(java.net.URI.create("/ui/docs/releasenotes/RELEASENOTES.md")).build()
}
