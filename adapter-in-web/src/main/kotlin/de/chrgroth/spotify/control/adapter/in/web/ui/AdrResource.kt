package de.chrgroth.spotify.control.adapter.`in`.web.ui

import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/ui/adr")
@ApplicationScoped
@Suppress("Unused")
class AdrResource {

  @Inject
  @Location("ui/docs.html")
  private lateinit var docsTemplate: Template

  @GET
  @Authenticated
  @Path("/{filename}")
  @Produces(MediaType.TEXT_HTML)
  fun adr(@PathParam("filename") filename: String): TemplateInstance {
    if (!filename.endsWith(".md") || filename.contains("/") || filename.contains("..")) {
      throw NotFoundException("ADR not found: $filename")
    }
    val content = DocsUtils.readMarkdown("docs/adr/$filename") ?: throw NotFoundException("ADR not found: $filename")
    return docsTemplate.instance()
      .data("title", DocsUtils.extractTitle(content, filename))
      .data("markdownContent", content)
  }
}
