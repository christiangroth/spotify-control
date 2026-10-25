package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.model.catalog.AlbumBrowseItem
import de.chrgroth.spotify.control.domain.model.catalog.ArtistBrowseItem
import de.chrgroth.spotify.control.domain.model.catalog.SyncTraceDisplay
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogBrowserPort
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogPort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

/**
 * Token API (see [ApiTokenAuthMechanism]): catalog browsing, sync traces and the sync timeline as JSON, plus
 * enqueueing artist syncs.
 */
@Path("/api/v1/catalog")
@ApplicationScoped
@Suppress("Unused")
class ApiCatalogResource(
  private val catalogBrowser: CatalogBrowserPort,
  private val catalog: CatalogPort,
  private val playbackSettings: PlaybackSettingsResource,
  private val httpResponseMetrics: ResponseTimingPort,
) {

  @GET
  @Path("/artists")
  @RolesAllowed(ApiTokenRoles.READ)
  @Produces(MediaType.APPLICATION_JSON)
  fun artists(@QueryParam("filter") filter: String?): Response = httpResponseMetrics.timed("rest.api.catalog.artists") {
    Response.ok(catalogBrowser.getArtists(filter).map { it.toJson() }).build()
  }

  @GET
  @Path("/artists/shallow")
  @RolesAllowed(ApiTokenRoles.READ)
  @Produces(MediaType.APPLICATION_JSON)
  fun shallowArtists(): Response = httpResponseMetrics.timed("rest.api.catalog.shallow-artists") {
    Response.ok(catalogBrowser.getShallowArtists().map { it.toJson() }).build()
  }

  @GET
  @Path("/artists/{artistId}/albums")
  @RolesAllowed(ApiTokenRoles.READ)
  @Produces(MediaType.APPLICATION_JSON)
  fun artistAlbums(@PathParam("artistId") artistId: String): Response = httpResponseMetrics.timed("rest.api.catalog.artist-albums") {
    Response.ok(catalogBrowser.getArtistAlbums(artistId).map { it.toJson() }).build()
  }

  @GET
  @Path("/albums")
  @RolesAllowed(ApiTokenRoles.READ)
  @Produces(MediaType.APPLICATION_JSON)
  fun albums(@QueryParam("filter") filter: String?): Response = httpResponseMetrics.timed("rest.api.catalog.albums") {
    Response.ok(catalogBrowser.getAlbums(filter).map { it.toJson() }).build()
  }

  @GET
  @Path("/albums/{albumId}/tracks")
  @RolesAllowed(ApiTokenRoles.READ)
  @Produces(MediaType.APPLICATION_JSON)
  fun albumTracks(@PathParam("albumId") albumId: String): Response = httpResponseMetrics.timed("rest.api.catalog.album-tracks") {
    Response.ok(
      catalogBrowser.getAlbumTracks(albumId).map {
        mapOf(
          "trackId" to it.trackId,
          "trackNumber" to it.trackNumber,
          "discNumber" to it.discNumber,
          "title" to it.title,
          "durationMs" to it.durationMs,
        )
      },
    ).build()
  }

  @GET
  @Path("/artists/{artistId}/sync-trace")
  @RolesAllowed(ApiTokenRoles.READ)
  @Produces(MediaType.APPLICATION_JSON)
  fun artistSyncTrace(@PathParam("artistId") artistId: String): Response = httpResponseMetrics.timed("rest.api.catalog.artist-sync-trace") {
    catalogBrowser.getArtistSyncTrace(artistId).toResponse()
  }

  @GET
  @Path("/albums/{albumId}/sync-trace")
  @RolesAllowed(ApiTokenRoles.READ)
  @Produces(MediaType.APPLICATION_JSON)
  fun albumSyncTrace(@PathParam("albumId") albumId: String): Response = httpResponseMetrics.timed("rest.api.catalog.album-sync-trace") {
    catalogBrowser.getAlbumSyncTrace(albumId).toResponse()
  }

  @GET
  @Path("/sync-timeline")
  @RolesAllowed(ApiTokenRoles.READ)
  @Produces(MediaType.APPLICATION_JSON)
  fun syncTimeline(
    @QueryParam("artistOffset") @DefaultValue("0") artistOffset: Int,
    @QueryParam("albumOffset") @DefaultValue("0") albumOffset: Int,
    @QueryParam("limit") @DefaultValue("50") limit: Int,
  ): Response = httpResponseMetrics.timed("rest.api.catalog.sync-timeline") {
    val page = catalogBrowser.getSyncTimeline(artistOffset.coerceAtLeast(0), albumOffset.coerceAtLeast(0), limit.coerceIn(1, MAX_TIMELINE_LIMIT))
    Response.ok(
      mapOf(
        "entries" to page.entries.map {
          mapOf(
            "entityType" to it.entityType.name,
            "syncedAt" to it.syncedAt.toString(),
            "artistId" to it.artistId,
            "artistName" to it.artistName,
            "albumCount" to it.albumCount,
            "albumId" to it.albumId,
            "albumName" to it.albumName,
            "trackCount" to it.trackCount,
          )
        },
        "nextArtistOffset" to page.nextArtistOffset,
        "nextAlbumOffset" to page.nextAlbumOffset,
        "hasMore" to page.hasMore,
      ),
    ).build()
  }

  @POST
  @Path("/artists/{artistId}/resync")
  @RolesAllowed(ApiTokenRoles.WRITE)
  @Produces(MediaType.APPLICATION_JSON)
  fun resyncArtist(@PathParam("artistId") artistId: String): Response = playbackSettings.resyncArtist(artistId)

  @POST
  @Path("/sync-missing-artists")
  @RolesAllowed(ApiTokenRoles.WRITE)
  @Produces(MediaType.APPLICATION_JSON)
  fun syncMissingArtists(): Response = httpResponseMetrics.timed("rest.api.catalog.sync-missing-artists") {
    catalog.enqueuePlaybackArtistsForSync()
    Response.ok(mapOf("status" to "ok")).build()
  }

  private fun ArtistBrowseItem.toJson() = mapOf(
    "artistId" to artistId,
    "artistName" to artistName,
    "albumCount" to albumCount,
    "trackCount" to trackCount,
    "syncStatus" to syncStatus.name,
  )

  private fun AlbumBrowseItem.toJson() = mapOf(
    "albumId" to albumId,
    "title" to title,
    "artistName" to artistName,
    "releaseDate" to releaseDate,
    "trackCount" to trackCount,
    "durationMs" to durationMs,
  )

  private fun SyncTraceDisplay?.toResponse(): Response =
    if (this == null) {
      Response.status(Response.Status.NOT_FOUND).entity(mapOf("error" to "No sync trace recorded")).build()
    } else {
      Response.ok(mapOf("description" to description, "triggeredAt" to triggeredAt.toString())).build()
    }

  companion object {
    private const val MAX_TIMELINE_LIMIT = 200
  }
}
