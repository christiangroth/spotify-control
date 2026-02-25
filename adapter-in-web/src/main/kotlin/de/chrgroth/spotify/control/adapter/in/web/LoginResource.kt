package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import jakarta.annotation.security.PermitAll
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/")
@ApplicationScoped
@Suppress("Unused")
class LoginResource {

  @Inject
  @Location("login.html")
  private lateinit var loginTemplate: Template

  @GET
  @Produces(MediaType.TEXT_HTML)
  @PermitAll
  fun index(): TemplateInstance = loginTemplate.instance()
}
