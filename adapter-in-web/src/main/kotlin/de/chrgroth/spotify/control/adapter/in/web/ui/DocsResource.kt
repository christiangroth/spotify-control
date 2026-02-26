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
import jakarta.ws.rs.core.Response
import mu.KLogging

@Path("/ui/docs")
@ApplicationScoped
@Suppress("Unused")
class DocsResource {

  @Inject
  @Location("ui/docs.html")
  private lateinit var docsTemplate: Template

  @Inject
  @Location("ui/docs-adr-index.html")
  private lateinit var docsAdrIndexTemplate: Template

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
      .data("markdownContent", readMarkdown("docs/arc42/arc42-EN.md"))

  @GET
  @Authenticated
  @Path("/adr")
  @Produces(MediaType.TEXT_HTML)
  fun adrIndex(): TemplateInstance =
    docsAdrIndexTemplate.instance()
      .data("title", "Architecture Decision Records")
      .data("adrs", listAdrFiles())

  @GET
  @Authenticated
  @Path("/adr/{filename}")
  @Produces(MediaType.TEXT_HTML)
  fun adr(@PathParam("filename") filename: String): TemplateInstance {
    if (!filename.endsWith(".md") || filename.contains("/") || filename.contains("..")) {
      logger.warn { "Invalid ADR filename requested: $filename" }
      throw NotFoundException("ADR not found: $filename")
    }
    val content = readMarkdown("docs/adr/$filename") ?: throw NotFoundException("ADR not found: $filename")
    return docsTemplate.instance()
      .data("title", extractTitle(content, filename))
      .data("markdownContent", content)
  }

  @GET
  @Authenticated
  @Path("/releasenotes")
  @Produces(MediaType.TEXT_HTML)
  fun releasenotes(): TemplateInstance =
    docsTemplate.instance()
      .data("title", "Release Notes")
      .data("markdownContent", readMarkdown("docs/releasenotes/RELEASENOTES.md"))

  private fun readMarkdown(resourcePath: String): String? {
    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
      ?: return null
    return stream.bufferedReader(Charsets.UTF_8).readText()
  }

  private fun listAdrFiles(): List<AdrEntry> {
    val index = readMarkdown("docs/adr/index.txt") ?: return emptyList()
    return index.lines()
      .filter { it.isNotBlank() }
      .mapNotNull { filename ->
        val content = readMarkdown("docs/adr/$filename") ?: return@mapNotNull null
        AdrEntry(filename, extractTitle(content, filename))
      }
  }

  private fun extractTitle(content: String, fallback: String): String =
    content.lineSequence()
      .firstOrNull { it.startsWith("# ") }
      ?.removePrefix("# ")
      ?.trim()
      ?: fallback

  data class AdrEntry(val filename: String, val title: String)

  companion object : KLogging()
}
