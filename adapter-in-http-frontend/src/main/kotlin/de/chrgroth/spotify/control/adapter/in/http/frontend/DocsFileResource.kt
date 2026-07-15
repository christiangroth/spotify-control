package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.adapter.`in`.http.metrics.HttpResponseMetrics
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Path("/docs/{subdir}/{filename}")
@ApplicationScoped
@Suppress("Unused")
class DocsFileResource(
  @param:Location("docs.html")
  private val docsTemplate: Template,
  private val httpResponseMetrics: HttpResponseMetrics,
) {

  private val allowedSubdirs = setOf("arc42", "adr", "coding-guidelines")

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun docs(
    @PathParam("subdir") subdir: String,
    @PathParam("filename") filename: String,
  ): TemplateInstance = httpResponseMetrics.timed("page.docs.view") {
    val decodedFilename = URLDecoder.decode(filename, StandardCharsets.UTF_8)
    if (subdir !in allowedSubdirs || isInvalidFilename(decodedFilename)) {
      throw NotFoundException("Doc not found: $subdir/$filename")
    }
    val content = DocsUtils.readMarkdown("docs/$subdir/$decodedFilename")
      ?: throw NotFoundException("Doc not found: $subdir/$filename")
    docsTemplate.instance()
      .data("title", DocsUtils.extractTitle(content, decodedFilename))
      .data("markdownContent", content)
  }

  private fun isInvalidFilename(filename: String): Boolean =
    !filename.endsWith(".md") || filename.contains("/") || filename.contains("..")
}
