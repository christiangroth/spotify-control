package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n.CatalogMessages
import de.chrgroth.spotify.control.domain.model.catalog.AlbumBrowseItem
import de.chrgroth.spotify.control.domain.model.catalog.ArtistBrowseItem
import de.chrgroth.spotify.control.domain.model.catalog.TrackBrowseItem
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogBrowserPort
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogPort
import de.chrgroth.spotify.control.domain.port.`in`.infra.DashboardPort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
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

@Path("/catalog")
@ApplicationScoped
@Suppress("Unused")
class CatalogResource(
  private val catalogBrowser: CatalogBrowserPort,
  private val catalog: CatalogPort,
  private val dashboard: DashboardPort,
  private val httpResponseMetrics: ResponseTimingPort,
  private val messages: CatalogMessages,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun catalog(@QueryParam("filter") filter: String?): TemplateInstance = httpResponseMetrics.timed("page.catalog.view") { details ->
    val filterActive = !filter.isNullOrBlank()
    val artists = details.detail("catalog.view.artists") { if (filterActive) catalogBrowser.getArtists(filter) else emptyList<ArtistBrowseItem>() }
    val catalogStats = details.detail("catalog.view.stats") { dashboard.getCatalogStats().catalogStats }
    Templates.catalog(artists, filter ?: "", filterActive, emptyList<AlbumBrowseItem>(), emptyList<TrackBrowseItem>(), catalogStats)
  }

  @GET
  @Path("/artists")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun artistList(@QueryParam("filter") filter: String?): TemplateInstance = httpResponseMetrics.timed("fragment.catalog.artist-list") {
    val filterActive = !filter.isNullOrBlank()
    val artists = if (filterActive) catalogBrowser.getArtists(filter) else emptyList<ArtistBrowseItem>()
    Templates.`catalog$snippet_artist_list`(artists, filter ?: "", filterActive)
  }

  @GET
  @Path("/artists/settings")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun artistSettings(): TemplateInstance = httpResponseMetrics.timed("fragment.catalog.artist-settings") { details ->
    val artists = details.detail("catalog.artist-settings.undecided-artists") { catalogBrowser.getUndecidedArtists() }
    val totalUndecidedCount = details.detail("catalog.artist-settings.stats") { catalogBrowser.getCatalogStats().undecidedArtistCount }
    Templates.`catalog-artists-settings`(artists, totalUndecidedCount > artists.size)
  }

  @GET
  @Path("/artists/shallow")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun shallowArtists(): TemplateInstance = httpResponseMetrics.timed("fragment.catalog.shallow-artists") { details ->
    val artists = details.detail("catalog.shallow-artists.artists") { catalogBrowser.getShallowArtists() }
    val totalShallowCount = details.detail("catalog.shallow-artists.stats") { catalogBrowser.getCatalogStats().shallowArtistCount }
    Templates.`catalog-shallow-artists`(artists, totalShallowCount > artists.size)
  }

  @GET
  @Path("/artists/{artistId}/albums")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun artistAlbums(@PathParam("artistId") artistId: String): TemplateInstance = httpResponseMetrics.timed("fragment.catalog.artist-albums") {
    Templates.`catalog$snippet_album_list`(catalogBrowser.getArtistAlbums(artistId), artistId)
  }

  @GET
  @Path("/albums/{albumId}/tracks")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun albumTracks(@PathParam("albumId") albumId: String): TemplateInstance = httpResponseMetrics.timed("fragment.catalog.album-tracks") {
    Templates.`catalog$snippet_track_list`(catalogBrowser.getAlbumTracks(albumId))
  }

  @POST
  @Authenticated
  @Path("/wipe")
  @Produces(MediaType.APPLICATION_JSON)
  fun wipeCatalog(): Response = httpResponseMetrics.timed("rest.catalog.wipe") {
    catalog.enqueueWipeCatalog()
    Response.ok(mapOf("status" to "ok")).build()
  }

  @GET
  @Path("/artists/{artistId}/sync-trace")
  @Authenticated
  @Produces(MediaType.TEXT_PLAIN)
  fun artistSyncTrace(@PathParam("artistId") artistId: String): Response = httpResponseMetrics.timed("rest.catalog.artist-sync-trace") {
    val trace = catalogBrowser.getArtistSyncTrace(artistId) ?: return@timed Response.ok(messages.catalogSyncTraceNotAvailable()).build()
    Response.ok("${trace.description} (${trace.triggeredAt})").build()
  }

  @GET
  @Path("/albums/{albumId}/sync-trace")
  @Authenticated
  @Produces(MediaType.TEXT_PLAIN)
  fun albumSyncTrace(@PathParam("albumId") albumId: String): Response = httpResponseMetrics.timed("rest.catalog.album-sync-trace") {
    val trace = catalogBrowser.getAlbumSyncTrace(albumId) ?: return@timed Response.ok(messages.catalogSyncTraceNotAvailable()).build()
    Response.ok("${trace.description} (${trace.triggeredAt})").build()
  }
}
