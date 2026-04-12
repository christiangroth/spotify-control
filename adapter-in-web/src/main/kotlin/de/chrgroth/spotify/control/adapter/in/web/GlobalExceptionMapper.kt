package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.qute.Location
import io.quarkus.qute.Template
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import java.io.PrintWriter
import java.io.StringWriter

@Provider
@ApplicationScoped
@Suppress("Unused")
class GlobalExceptionMapper(
  @param:Location("error.html")
  private val errorTemplate: Template,
) : ExceptionMapper<Throwable> {

  override fun toResponse(exception: Throwable): Response {
    val status = when (exception) {
      is WebApplicationException -> exception.response.status
      else -> Response.Status.INTERNAL_SERVER_ERROR.statusCode
    }
    val stackTrace = StringWriter().also { exception.printStackTrace(PrintWriter(it)) }.toString()
    val html = errorTemplate
      .data("statusCode", status)
      .data("errorType", exception.javaClass.name)
      .data("message", exception.message ?: "(no message)")
      .data("stackTrace", stackTrace)
      .render()
    return Response
      .status(status)
      .type(MediaType.TEXT_HTML_TYPE)
      .entity(html)
      .build()
  }
}
