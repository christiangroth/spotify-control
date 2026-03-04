package de.chrgroth.spotify.control.adapter.`in`.web.ui

import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.core.Response

@Path("/ui/adr")
@ApplicationScoped
@Suppress("Unused")
class AdrResource {

  @GET
  @Authenticated
  @Path("/{filename}")
  fun adr(@PathParam("filename") filename: String): Response {
    if (!filename.endsWith(".md") || filename.contains("/") || filename.contains("..")) {
      throw NotFoundException("ADR not found: $filename")
    }
    return Response.seeOther(java.net.URI.create("/ui/docs/adr/$filename")).build()
  }
}
