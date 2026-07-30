package de.chrgroth.spotify.control.adapter.out.spotify

import de.chrgroth.spotify.control.adapter.out.spotify.model.AlbumObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.ArtistObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.CurrentlyPlayingContextObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.CursorPagingPlayHistoryObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.PagingArtistDiscographyAlbumObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.PagingPlaylistObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.PagingPlaylistTrackObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.PagingSimplifiedTrackObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.PrivateUserObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.SeveralArtistsObject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient

@RegisterRestClient(configKey = "spotify-api")
@RegisterProvider(SpotifyApiBearerFilter::class)
@RegisterProvider(SpotifyResponseExceptionMapper::class)
@RegisterProvider(KotlinxSerializationReader::class)
interface SpotifyApiRestClient {

  @GET
  @Path("/v1/me")
  @Produces(MediaType.APPLICATION_JSON)
  fun getCurrentUserProfile(): PrivateUserObject

  @GET
  @Path("/v1/me/player/currently-playing")
  @Produces(MediaType.APPLICATION_JSON)
  fun getCurrentlyPlaying(): CurrentlyPlayingContextObject

  @GET
  @Path("/v1/me/player/recently-played")
  @Produces(MediaType.APPLICATION_JSON)
  fun getRecentlyPlayed(
    @QueryParam("limit") limit: Int,
    @QueryParam("after") after: Long?,
  ): CursorPagingPlayHistoryObject

  @GET
  @Path("/v1/artists/{artistId}")
  @Produces(MediaType.APPLICATION_JSON)
  fun getArtist(
    @PathParam("artistId") artistId: String,
  ): ArtistObject

  @GET
  @Path("/v1/artists")
  @Produces(MediaType.APPLICATION_JSON)
  fun getSeveralArtists(
    @QueryParam("ids") ids: String,
  ): SeveralArtistsObject

  @GET
  @Path("/v1/artists/{artistId}/albums")
  @Produces(MediaType.APPLICATION_JSON)
  fun getArtistAlbums(
    @PathParam("artistId") artistId: String,
    @QueryParam("limit") limit: Int,
    @QueryParam("offset") offset: Int?,
    @QueryParam("include_groups") includeGroups: String?,
  ): PagingArtistDiscographyAlbumObject

  @GET
  @Path("/v1/albums/{albumId}")
  @Produces(MediaType.APPLICATION_JSON)
  fun getAlbum(
    @PathParam("albumId") albumId: String,
  ): AlbumObject

  @GET
  @Path("/v1/albums/{albumId}/tracks")
  @Produces(MediaType.APPLICATION_JSON)
  fun getAlbumTracks(
    @PathParam("albumId") albumId: String,
    @QueryParam("limit") limit: Int,
    @QueryParam("offset") offset: Int?,
  ): PagingSimplifiedTrackObject

  @GET
  @Path("/v1/me/playlists")
  @Produces(MediaType.APPLICATION_JSON)
  fun getUserPlaylists(
    @QueryParam("limit") limit: Int,
    @QueryParam("offset") offset: Int?,
  ): PagingPlaylistObject

  @GET
  @Path("/v1/playlists/{playlistId}/items")
  @Produces(MediaType.APPLICATION_JSON)
  fun getPlaylistItems(
    @PathParam("playlistId") playlistId: String,
    @QueryParam("limit") limit: Int,
    @QueryParam("offset") offset: Int?,
  ): PagingPlaylistTrackObject

  @DELETE
  @Path("/v1/playlists/{playlistId}/items")
  @Consumes(MediaType.APPLICATION_JSON)
  fun removePlaylistItems(
    @PathParam("playlistId") playlistId: String,
    body: String,
  )

  @POST
  @Path("/v1/playlists/{playlistId}/items")
  @Consumes(MediaType.APPLICATION_JSON)
  fun addPlaylistItems(
    @PathParam("playlistId") playlistId: String,
    body: String,
  )

  @DELETE
  @Path("/v1/playlists/{playlistId}/tracks")
  @Consumes(MediaType.APPLICATION_JSON)
  fun removePlaylistItemsByPosition(
    @PathParam("playlistId") playlistId: String,
    body: String,
  )

  @POST
  @Path("/v1/playlists/{playlistId}/tracks")
  @Consumes(MediaType.APPLICATION_JSON)
  fun addPlaylistItemsToTracks(
    @PathParam("playlistId") playlistId: String,
    body: String,
  )
}
