package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.model.AlbumBrowseItem
import de.chrgroth.spotify.control.domain.model.ArtistBrowseItem
import de.chrgroth.spotify.control.domain.model.TrackBrowseItem
import de.chrgroth.spotify.control.domain.port.`in`.CatalogBrowserPort
import de.chrgroth.spotify.control.domain.port.`in`.CatalogPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
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
class CatalogResource {

    @Inject
    @Location("catalog.html")
    private lateinit var catalogTemplate: Template

    @Inject
    private lateinit var catalogBrowser: CatalogBrowserPort

    @Inject
    private lateinit var catalog: CatalogPort

    @GET
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    fun catalog(@QueryParam("filter") filter: String?): TemplateInstance {
        val filterActive = !filter.isNullOrBlank()
        val artists = if (filterActive) catalogBrowser.getArtists(filter) else emptyList<ArtistBrowseItem>()
        return catalogTemplate
            .data("artists", artists)
            .data("filter", filter ?: "")
            .data("filterActive", filterActive)
            .data("albums", emptyList<AlbumBrowseItem>())
            .data("tracks", emptyList<TrackBrowseItem>())
    }

    @GET
    @Path("/artists")
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    fun artistList(@QueryParam("filter") filter: String?): TemplateInstance {
        val filterActive = !filter.isNullOrBlank()
        val artists = if (filterActive) catalogBrowser.getArtists(filter) else emptyList<ArtistBrowseItem>()
        return catalogTemplate.getFragment("fragment_artist_list")
            .data("artists", artists)
            .data("filter", filter ?: "")
            .data("filterActive", filterActive)
    }

    @GET
    @Path("/artists/{artistId}/albums")
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    fun artistAlbums(@PathParam("artistId") artistId: String): TemplateInstance {
        val albums = catalogBrowser.getArtistAlbums(artistId)
        return catalogTemplate.getFragment("fragment_album_list")
            .data("albums", albums)
            .data("artistId", artistId)
    }

    @GET
    @Path("/albums/{albumId}/tracks")
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    fun albumTracks(@PathParam("albumId") albumId: String): TemplateInstance {
        val tracks = catalogBrowser.getAlbumTracks(albumId)
        return catalogTemplate.getFragment("fragment_track_list")
            .data("tracks", tracks)
    }

    @POST
    @Authenticated
    @Path("/wipe")
    @Produces(MediaType.APPLICATION_JSON)
    fun wipeCatalog(): Response {
        return catalog.wipeCatalog().fold(
            ifLeft = { error ->
                logger.error { "Catalog wipe failed: ${error.code}" }
                Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(mapOf("error" to "Wipe failed: ${error.code}"))
                    .build()
            },
            ifRight = { Response.ok(mapOf("status" to "ok")).build() },
        )
    }

    companion object : KLogging()
}
