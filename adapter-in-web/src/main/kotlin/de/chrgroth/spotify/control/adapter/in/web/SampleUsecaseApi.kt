package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.adapter.incoming.web.api.SampleApi
import de.chrgroth.spotify.control.adapter.incoming.web.api.model.HelloResponse
import de.chrgroth.spotify.control.domain.port.`in`.SampleUsecasePort
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
@Authenticated
@Suppress("Unused")
class SampleUsecaseApi : SampleApi {

  @Inject
  private lateinit var domain: SampleUsecasePort

  override fun getHello(): HelloResponse =
    HelloResponse(
      message = domain.sayHello(),
    )
}
