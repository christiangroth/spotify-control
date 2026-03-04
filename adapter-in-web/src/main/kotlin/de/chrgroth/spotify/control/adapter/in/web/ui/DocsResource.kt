package de.chrgroth.spotify.control.adapter.`in`.web.ui

import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/ui/docs")
@ApplicationScoped
@Suppress("Unused")
class DocsResource {

  @Inject
  @Location("ui/docs.html")
  private lateinit var docsTemplate: Template

  @GET
  @Authenticated
  fun redirectToDocs(): Response = Response.seeOther(java.net.URI.create("/ui/docs/arc42")).build()

  @GET
  @Authenticated
  @Path("/arc42")
  @Produces(MediaType.TEXT_HTML)
  fun arc42(): TemplateInstance =
    docsTemplate.instance()
      .data("title", "Architecture Documentation")
      .data("markdownContent", DocsUtils.readMarkdown("docs/arc42/arc42.md"))

  @GET
  @Authenticated
  @Path("/starters")
  @Produces(MediaType.TEXT_HTML)
  fun starters(): TemplateInstance =
    docsTemplate.instance()
      .data("title", "Starters")
      .data("markdownContent", DocsUtils.readMarkdown("docs/arc42/starters.md"))

  @GET
  @Authenticated
  @Path("/outbox")
  @Produces(MediaType.TEXT_HTML)
  fun outbox(): TemplateInstance =
    docsTemplate.instance()
      .data("title", "Outbox")
      .data("markdownContent", DocsUtils.readMarkdown("docs/arc42/outbox.md"))

  @GET
  @Authenticated
  @Path("/releasenotes")
  @Produces(MediaType.TEXT_HTML)
  fun releasenotes(): TemplateInstance =
    docsTemplate.instance()
      .data("title", "Release Notes")
      .data("markdownContent", DocsUtils.readMarkdown("docs/releasenotes/RELEASENOTES.md"))
}
