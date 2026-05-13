package de.chrgroth.spotify.control.adapter.out.spotify

import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient

@RegisterRestClient(configKey = "spotify-api")
interface SpotifyApiRestClient {

  @GET
  @Path("/v1/me")
  @Produces(MediaType.APPLICATION_JSON)
  fun getCurrentUserProfile(@HeaderParam("Authorization") authorization: String): Response

  @GET
  @Path("/v1/me/player/currently-playing")
  @Produces(MediaType.APPLICATION_JSON)
  fun getCurrentlyPlaying(@HeaderParam("Authorization") authorization: String): Response

  @GET
  @Path("/v1/me/player/recently-played")
  @Produces(MediaType.APPLICATION_JSON)
  fun getRecentlyPlayed(
    @HeaderParam("Authorization") authorization: String,
    @QueryParam("limit") limit: Int,
    @QueryParam("after") after: Long?,
  ): Response

  @GET
  @Path("/v1/artists/{artistId}")
  @Produces(MediaType.APPLICATION_JSON)
  fun getArtist(
    @HeaderParam("Authorization") authorization: String,
    @PathParam("artistId") artistId: String,
  ): Response

  @GET
  @Path("/v1/artists/{artistId}/albums")
  @Produces(MediaType.APPLICATION_JSON)
  fun getArtistAlbums(
    @HeaderParam("Authorization") authorization: String,
    @PathParam("artistId") artistId: String,
    @QueryParam("limit") limit: Int,
    @QueryParam("offset") offset: Int?,
  ): Response

  @GET
  @Path("/v1/albums/{albumId}")
  @Produces(MediaType.APPLICATION_JSON)
  fun getAlbum(
    @HeaderParam("Authorization") authorization: String,
    @PathParam("albumId") albumId: String,
  ): Response

  @GET
  @Path("/v1/albums/{albumId}/tracks")
  @Produces(MediaType.APPLICATION_JSON)
  fun getAlbumTracks(
    @HeaderParam("Authorization") authorization: String,
    @PathParam("albumId") albumId: String,
    @QueryParam("limit") limit: Int,
    @QueryParam("offset") offset: Int?,
  ): Response

  @GET
  @Path("/v1/me/playlists")
  @Produces(MediaType.APPLICATION_JSON)
  fun getUserPlaylists(
    @HeaderParam("Authorization") authorization: String,
    @QueryParam("limit") limit: Int,
    @QueryParam("offset") offset: Int?,
  ): Response

  @GET
  @Path("/v1/playlists/{playlistId}/items")
  @Produces(MediaType.APPLICATION_JSON)
  fun getPlaylistItems(
    @HeaderParam("Authorization") authorization: String,
    @PathParam("playlistId") playlistId: String,
    @QueryParam("limit") limit: Int,
    @QueryParam("offset") offset: Int?,
  ): Response

  @DELETE
  @Path("/v1/playlists/{playlistId}/items")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun removePlaylistItems(
    @HeaderParam("Authorization") authorization: String,
    @PathParam("playlistId") playlistId: String,
    body: String,
  ): Response

  @POST
  @Path("/v1/playlists/{playlistId}/items")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun addPlaylistItems(
    @HeaderParam("Authorization") authorization: String,
    @PathParam("playlistId") playlistId: String,
    body: String,
  ): Response

  @DELETE
  @Path("/v1/playlists/{playlistId}/tracks")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun removePlaylistItemsByPosition(
    @HeaderParam("Authorization") authorization: String,
    @PathParam("playlistId") playlistId: String,
    body: String,
  ): Response

  @POST
  @Path("/v1/playlists/{playlistId}/tracks")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun addPlaylistItemsToTracks(
    @HeaderParam("Authorization") authorization: String,
    @PathParam("playlistId") playlistId: String,
    body: String,
  ): Response
}
