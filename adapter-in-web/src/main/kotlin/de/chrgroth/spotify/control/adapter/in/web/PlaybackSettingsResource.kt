package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.error.ArtistSettingsError
import mu.KLogging
import de.chrgroth.spotify.control.domain.model.ArtistPlaybackProcessingStatus
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.CatalogPort
import de.chrgroth.spotify.control.domain.port.`in`.PlaybackPort
import de.chrgroth.spotify.control.domain.port.out.MongoReadTimeoutPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

@Path("/settings/playback")
@ApplicationScoped
@Suppress("Unused")
class PlaybackSettingsResource {

  @Inject
  @Location("settings/playback.html")
  private lateinit var playbackTemplate: Template

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  @Inject
  private lateinit var userRepository: UserRepositoryPort

  @Inject
  private lateinit var playback: PlaybackPort

  @Inject
  private lateinit var catalog: CatalogPort

  @Inject
  private lateinit var readTimeout: MongoReadTimeoutPort

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun playback(): TemplateInstance {
    val userId = UserId(securityIdentity.principal.name)
    val contextClassLoader = Thread.currentThread().contextClassLoader
    val (user, allArtists) = runBlocking {
      val userAsync = async(Dispatchers.IO) {
        Thread.currentThread().contextClassLoader = contextClassLoader
        readTimeout.timedWithFallback("userRepository.findById", null) { userRepository.findById(userId) }
      }
      val artistsAsync = async(Dispatchers.IO) {
        Thread.currentThread().contextClassLoader = contextClassLoader
        readTimeout.timedWithFallback("catalog.findAllArtists", emptyList()) { catalog.findAllArtists() }
      }
      Pair(userAsync.await(), artistsAsync.await().sortedBy { it.artistName })
    }
    return playbackTemplate
      .data("displayName", user?.displayName ?: userId.value)
      .data("undecidedArtists", allArtists.filter { it.playbackProcessingStatus == ArtistPlaybackProcessingStatus.UNDECIDED })
      .data("activeArtists", allArtists.filter { it.playbackProcessingStatus == ArtistPlaybackProcessingStatus.ACTIVE })
      .data("inactiveArtists", allArtists.filter { it.playbackProcessingStatus == ArtistPlaybackProcessingStatus.INACTIVE })
  }

  @POST
  @Authenticated
  @Path("/rebuild")
  @Produces(MediaType.APPLICATION_JSON)
  fun rebuildPlaybackData(): Response {
    val userId = UserId(securityIdentity.principal.name)
    playback.enqueueRebuildPlaybackData(userId)
    return Response.ok(mapOf("status" to "ok")).build()
  }

  @POST
  @Authenticated
  @Path("/sync-from-playlists")
  @Produces(MediaType.APPLICATION_JSON)
  fun syncArtistPlaybackFromPlaylists(): Response {
    val userId = UserId(securityIdentity.principal.name)
    playback.syncArtistPlaybackFromPlaylists(userId)
    return Response.ok(mapOf("status" to "ok")).build()
  }

  @POST
  @Authenticated
  @Path("/resync-catalog")
  @Produces(MediaType.APPLICATION_JSON)
  fun resyncCatalog(): Response {
    return catalog.resyncCatalog().fold(
      ifLeft = { error ->
        logger.error { "Catalog re-sync failed: ${error.code}" }
        Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(mapOf("error" to "Re-sync failed: ${error.code}"))
          .build()
      },
      ifRight = { Response.ok(mapOf("status" to "ok")).build() },
    )
  }

  @POST
  @Authenticated
  @Path("/artists/{artistId}/resync")
  @Produces(MediaType.APPLICATION_JSON)
  fun resyncArtist(@PathParam("artistId") artistId: String): Response {
    return catalog.resyncArtist(artistId).fold(
      ifLeft = { error ->
        when (error) {
          ArtistSettingsError.ARTIST_NOT_FOUND -> {
            logger.warn { "Artist $artistId not found for re-sync: ${error.code}" }
            Response.status(Response.Status.NOT_FOUND)
              .entity(mapOf("error" to "Artist not found: $artistId"))
              .build()
          }
          else -> {
            logger.error { "Artist re-sync failed for $artistId: ${error.code}" }
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
              .entity(mapOf("error" to "Re-sync failed: ${error.code}"))
              .build()
          }
        }
      },
      ifRight = { Response.ok(mapOf("status" to "ok")).build() },
    )
  }

  @PUT
  @Authenticated
  @Path("/artists/{artistId}/status")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun updateArtistStatus(
    @PathParam("artistId") artistId: String,
    request: ArtistStatusRequest,
  ): Response {
    val userId = UserId(securityIdentity.principal.name)
    val status = ArtistPlaybackProcessingStatus.entries.find { it.name == request.status }
      ?: return Response.status(Response.Status.BAD_REQUEST)
        .entity(mapOf("error" to "Invalid artist status: ${request.status}"))
        .build()
    return catalog.updateArtistPlaybackProcessingStatus(artistId, status, userId).fold(
      ifLeft = { error ->
        when (error) {
          ArtistSettingsError.ARTIST_NOT_FOUND -> {
            logger.warn { "Artist $artistId not found for status update: ${error.code}" }
            Response.status(Response.Status.NOT_FOUND)
              .entity(mapOf("error" to "Artist not found: $artistId"))
              .build()
          }
          else -> {
            logger.error { "Artist status update failed for $artistId: ${error.code}" }
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
              .entity(mapOf("error" to "Update failed: ${error.code}"))
              .build()
          }
        }
      },
      ifRight = { Response.ok(mapOf("status" to status.name)).build() },
    )
  }

  data class ArtistStatusRequest(val status: String = "")

  companion object : KLogging()
}
