package de.chrgroth.spotify.control.adapter.out.spotify

import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyTokenResponse
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.FormParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient

@RegisterRestClient(configKey = "spotify-accounts")
@RegisterProvider(SpotifyAccountsBasicFilter::class)
@RegisterProvider(SpotifyResponseExceptionMapper::class)
@RegisterProvider(KotlinxSerializationReader::class)
interface SpotifyAccountsRestClient {

  @POST
  @Path("/api/token")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun exchangeCode(
    @FormParam("grant_type") grantType: String,
    @FormParam("code") code: String,
    @FormParam("redirect_uri") redirectUri: String,
  ): SpotifyTokenResponse

  @POST
  @Path("/api/token")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun refreshToken(
    @FormParam("grant_type") grantType: String,
    @FormParam("refresh_token") refreshToken: String,
  ): SpotifyTokenResponse
}
