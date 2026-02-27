package de.chrgroth.spotify.control.adapter.`in`.web.api

import de.chrgroth.spotify.control.adapter.incoming.web.api.SampleApi
import de.chrgroth.spotify.control.adapter.incoming.web.api.model.HelloResponse
import de.chrgroth.spotify.control.domain.port.`in`.SampleUsecasePort
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
@Suppress("Unused")
class SampleUsecaseApi : SampleApi {

  @Inject
  private lateinit var domain: SampleUsecasePort

  @Authenticated
  override fun getHello(): HelloResponse =
    HelloResponse(
      message = domain.sayHello(),
    )
}
