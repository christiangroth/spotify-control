package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.adapter.`in`.http.metrics.HttpResponseMetrics
import de.chrgroth.spotify.control.domain.model.catalog.AlbumBrowseItem
import de.chrgroth.spotify.control.domain.model.catalog.ArtistBrowseItem
import de.chrgroth.spotify.control.domain.model.catalog.TrackBrowseItem
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogBrowserPort
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogPort
import de.chrgroth.spotify.control.domain.port.`in`.infra.DashboardPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import mu.KLogging

@Path("/catalog")
@ApplicationScoped
@Suppress("Unused")
class CatalogResource(
  @param:Location("catalog.html")
  private val catalogTemplate: Template,
  @param:Location("catalog-artists-settings.html")
  private val artistSettingsTemplate: Template,
  private val catalogBrowser: CatalogBrowserPort,
  private val catalog: CatalogPort,
  private val dashboard: DashboardPort,
  private val httpResponseMetrics: HttpResponseMetrics,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun catalog(@QueryParam("filter") filter: String?): TemplateInstance = httpResponseMetrics.timed("page.catalog.view") { details ->
    val filterActive = !filter.isNullOrBlank()
    val artists = details.detail("catalog.view.artists") { if (filterActive) catalogBrowser.getArtists(filter) else emptyList<ArtistBrowseItem>() }
    val catalogStats = details.detail("catalog.view.stats") { dashboard.getCatalogStats().catalogStats }
    catalogTemplate
      .data("artists", artists)
      .data("filter", filter ?: "")
      .data("filterActive", filterActive)
      .data("albums", emptyList<AlbumBrowseItem>())
      .data("tracks", emptyList<TrackBrowseItem>())
      .data("catalogStats", catalogStats)
  }

  @GET
  @Path("/artists")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun artistList(@QueryParam("filter") filter: String?): TemplateInstance = httpResponseMetrics.timed("fragment.catalog.artist-list") {
    val filterActive = !filter.isNullOrBlank()
    val artists = if (filterActive) catalogBrowser.getArtists(filter) else emptyList<ArtistBrowseItem>()
    catalogTemplate.getFragment("snippet_artist_list")
      .data("artists", artists)
      .data("filter", filter ?: "")
      .data("filterActive", filterActive)
  }

  @GET
  @Path("/artists/settings")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun artistSettings(): TemplateInstance = httpResponseMetrics.timed("fragment.catalog.artist-settings") { details ->
    val artists = details.detail("catalog.artist-settings.undecided-artists") { catalogBrowser.getUndecidedArtists() }
    val totalUndecidedCount = details.detail("catalog.artist-settings.stats") { catalogBrowser.getCatalogStats().undecidedArtistCount }
    artistSettingsTemplate
      .data("artists", artists)
      .data("truncated", totalUndecidedCount > artists.size)
  }

  @GET
  @Path("/artists/{artistId}/albums")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun artistAlbums(@PathParam("artistId") artistId: String): TemplateInstance = httpResponseMetrics.timed("fragment.catalog.artist-albums") {
    val albums = catalogBrowser.getArtistAlbums(artistId)
    catalogTemplate.getFragment("snippet_album_list")
      .data("albums", albums)
      .data("artistId", artistId)
  }

  @GET
  @Path("/albums/{albumId}/tracks")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun albumTracks(@PathParam("albumId") albumId: String): TemplateInstance = httpResponseMetrics.timed("fragment.catalog.album-tracks") {
    val tracks = catalogBrowser.getAlbumTracks(albumId)
    catalogTemplate.getFragment("snippet_track_list")
      .data("tracks", tracks)
  }

  @POST
  @Authenticated
  @Path("/wipe")
  @Produces(MediaType.APPLICATION_JSON)
  fun wipeCatalog(): Response = httpResponseMetrics.timed("rest.catalog.wipe") {
    catalog.wipeCatalog().fold(
      ifLeft = { error ->
        logger.error { "Catalog wipe failed: ${error.code}" }
        Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(mapOf("error" to "Wipe failed: ${error.code}"))
          .build()
      },
      ifRight = { Response.ok(mapOf("status" to "ok")).build() },
    )
  }

  @GET
  @Path("/artists/{artistId}/sync-trace")
  @Authenticated
  @Produces(MediaType.TEXT_PLAIN)
  fun artistSyncTrace(@PathParam("artistId") artistId: String): Response = httpResponseMetrics.timed("rest.catalog.artist-sync-trace") {
    val trace = catalogBrowser.getArtistSyncTrace(artistId) ?: return@timed Response.ok(NO_TRACE_AVAILABLE).build()
    Response.ok("${trace.description} (${trace.triggeredAt})").build()
  }

  @GET
  @Path("/albums/{albumId}/sync-trace")
  @Authenticated
  @Produces(MediaType.TEXT_PLAIN)
  fun albumSyncTrace(@PathParam("albumId") albumId: String): Response = httpResponseMetrics.timed("rest.catalog.album-sync-trace") {
    val trace = catalogBrowser.getAlbumSyncTrace(albumId) ?: return@timed Response.ok(NO_TRACE_AVAILABLE).build()
    Response.ok("${trace.description} (${trace.triggeredAt})").build()
  }

  companion object : KLogging() {
    private const val NO_TRACE_AVAILABLE = "No sync trigger information available."
  }
}
