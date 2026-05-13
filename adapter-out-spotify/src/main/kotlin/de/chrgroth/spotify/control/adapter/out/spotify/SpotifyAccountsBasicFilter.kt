package de.chrgroth.spotify.control.adapter.out.spotify

import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.client.ClientRequestContext
import jakarta.ws.rs.client.ClientRequestFilter
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.util.Base64

@ApplicationScoped
internal class SpotifyAccountsBasicFilter(
  @param:ConfigProperty(name = "spotify.client-id") private val clientId: String,
  @param:ConfigProperty(name = "spotify.client-secret") private val clientSecret: String,
) : ClientRequestFilter {

  private val basicCredentials: String by lazy {
    "Basic ${Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())}"
  }

  override fun filter(requestContext: ClientRequestContext) {
    requestContext.headers.putSingle("Authorization", basicCredentials)
  }
}
