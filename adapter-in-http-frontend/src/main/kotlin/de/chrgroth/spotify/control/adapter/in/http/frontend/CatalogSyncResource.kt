package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogBrowserPort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/catalog-sync")
@ApplicationScoped
@Suppress("Unused")
class CatalogSyncResource(
  @param:Location("catalog-sync.html")
  private val template: Template,
  private val catalogBrowser: CatalogBrowserPort,
  private val httpResponseMetrics: ResponseTimingPort,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun page(): Response = httpResponseMetrics.timed("page.catalog.sync-timeline") { renderPage(0, 0) }

  @GET
  @Path("/snippet")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippet(
    @QueryParam("artistOffset") artistOffset: Int?,
    @QueryParam("albumOffset") albumOffset: Int?,
  ): Response = httpResponseMetrics.timed("fragment.catalog.sync-timeline-rows") { renderRows(artistOffset ?: 0, albumOffset ?: 0) }

  private fun renderPage(artistOffset: Int, albumOffset: Int): Response {
    val page = catalogBrowser.getSyncTimeline(artistOffset, albumOffset, PAGE_SIZE)
    val html = template.data("page", page).data("entries", page.entries).render()
    return Response.ok(html).build()
  }

  private fun renderRows(artistOffset: Int, albumOffset: Int): Response {
    val page = catalogBrowser.getSyncTimeline(artistOffset, albumOffset, PAGE_SIZE)
    val html = template.getFragment("snippet_rows").data("entries", page.entries).render()
    return Response.ok(html)
      .header(NEXT_ARTIST_OFFSET_HEADER, page.nextArtistOffset)
      .header(NEXT_ALBUM_OFFSET_HEADER, page.nextAlbumOffset)
      .header(HAS_MORE_HEADER, page.hasMore)
      .build()
  }

  companion object {
    private const val PAGE_SIZE = 200
    internal const val NEXT_ARTIST_OFFSET_HEADER = "X-Next-Artist-Offset"
    internal const val NEXT_ALBUM_OFFSET_HEADER = "X-Next-Album-Offset"
    internal const val HAS_MORE_HEADER = "X-Has-More"
  }
}
