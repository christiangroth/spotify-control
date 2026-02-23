package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.port.`in`.SampleUsecasePort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path

@ApplicationScoped
@Path("/api/hello")
@Suppress("Unused")
class SampleUsecaseApi {

  @Inject
  private lateinit var domain: SampleUsecasePort

  @GET
  fun sayHello(): String {
    return domain.sayHello()
  }
}
