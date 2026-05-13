package de.chrgroth.spotify.control.adapter.out.spotify

import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.client.ClientRequestContext
import jakarta.ws.rs.client.ClientRequestFilter

@ApplicationScoped
internal class SpotifyApiBearerFilter : ClientRequestFilter {

  override fun filter(requestContext: ClientRequestContext) {
    requestContext.headers.putSingle("Authorization", SpotifyApiAuthContext.get())
  }
}
