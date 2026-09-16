package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n.AppMessages
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import java.io.PrintWriter
import java.io.StringWriter
import org.eclipse.microprofile.config.inject.ConfigProperty

@Provider
@ApplicationScoped
@Suppress("Unused")
class GlobalExceptionMapper(
  private val messages: AppMessages,
  @param:ConfigProperty(name = "quarkus.application.version")
  private val appBuildVersion: String,
) : ExceptionMapper<Throwable> {

  override fun toResponse(exception: Throwable): Response {
    val status = when (exception) {
      is WebApplicationException -> exception.response.status
      else -> Response.Status.INTERNAL_SERVER_ERROR.statusCode
    }
    val stackTrace = StringWriter().also { exception.printStackTrace(PrintWriter(it)) }.toString()
    val html = Templates.error(status, exception.javaClass.name, exception.message ?: messages.errorNoMessage(), stackTrace, appBuildVersion).render()
    return Response
      .status(status)
      .type(MediaType.TEXT_HTML_TYPE)
      .entity(html)
      .build()
  }
}
