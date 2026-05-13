package de.chrgroth.spotify.control.adapter.out.spotify

import jakarta.ws.rs.Consumes
import jakarta.ws.rs.FormParam
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient

@RegisterRestClient(configKey = "spotify-accounts")
interface SpotifyAccountsRestClient {

  @POST
  @Path("/api/token")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun exchangeCode(
    @HeaderParam("Authorization") authorization: String,
    @FormParam("grant_type") grantType: String,
    @FormParam("code") code: String,
    @FormParam("redirect_uri") redirectUri: String,
  ): Response

  @POST
  @Path("/api/token")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun refreshToken(
    @HeaderParam("Authorization") authorization: String,
    @FormParam("grant_type") grantType: String,
    @FormParam("refresh_token") refreshToken: String,
  ): Response
}
