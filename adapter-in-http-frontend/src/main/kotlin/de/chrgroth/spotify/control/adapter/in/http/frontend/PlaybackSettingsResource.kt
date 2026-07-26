package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import arrow.core.Either
import de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n.PlaybackMessages
import de.chrgroth.spotify.control.domain.error.ArtistSettingsError
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogPort
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackAggregationPort
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackPort
import de.chrgroth.spotify.control.domain.port.`in`.user.UserProfilePort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import mu.KLogging

@Path("/settings/playback")
@ApplicationScoped
@Suppress("Unused")
class PlaybackSettingsResource(
  private val userProfile: UserProfilePort,
  private val playback: PlaybackPort,
  private val playbackAggregation: PlaybackAggregationPort,
  private val catalog: CatalogPort,
  private val httpResponseMetrics: ResponseTimingPort,
  private val messages: PlaybackMessages,
) {

  @POST
  @Authenticated
  @Path("/rebuild")
  @Produces(MediaType.APPLICATION_JSON)
  fun rebuildPlaybackData(): Response = httpResponseMetrics.timed("rest.playback.rebuild") {
    playback.enqueueRebuildPlaybackData()
    Response.ok(mapOf("status" to "ok")).build()
  }

  @POST
  @Authenticated
  @Path("/resync-catalog")
  @Produces(MediaType.APPLICATION_JSON)
  fun resyncCatalog(): Response = httpResponseMetrics.timed("rest.catalog.resync") {
    catalog.enqueueResyncCatalog()
    Response.ok(mapOf("status" to "ok")).build()
  }

  @POST
  @Authenticated
  @Path("/sync-missing-artists")
  @Produces(MediaType.APPLICATION_JSON)
  fun syncMissingArtists(): Response = httpResponseMetrics.timed("rest.catalog.sync-missing-artists") {
    catalog.enqueuePlaybackArtistsForSync()
    Response.ok(mapOf("status" to "ok")).build()
  }

  @POST
  @Authenticated
  @Path("/aggregations/rebuild")
  @Produces(MediaType.APPLICATION_JSON)
  fun rebuildAggregations(): Response = httpResponseMetrics.timed("rest.playback.aggregations-rebuild") {
    playbackAggregation.enqueueRebuildAllAggregations()
    Response.ok(mapOf("status" to "ok")).build()
  }

  @POST
  @Authenticated
  @Path("/profile/refresh")
  @Produces(MediaType.APPLICATION_JSON)
  fun refreshProfile(): Response = httpResponseMetrics.timed("rest.user-profile.refresh") {
    userProfile.enqueueUpdates()
    Response.ok(mapOf("status" to "ok")).build()
  }

  @POST
  @Authenticated
  @Path("/poll")
  @Produces(MediaType.APPLICATION_JSON)
  fun pollNow(): Response = httpResponseMetrics.timed("rest.playback.poll") {
    playback.enqueueFetchPlaybackData()
    Response.ok(mapOf("status" to "ok")).build()
  }

  @POST
  @Authenticated
  @Path("/artists/{artistId}/resync")
  @Produces(MediaType.APPLICATION_JSON)
  fun resyncArtist(@PathParam("artistId") artistId: String): Response =
    httpResponseMetrics.timed("rest.catalog.artist-resync") {
      catalog.resyncArtist(artistId).fold(
        ifLeft = { error ->
          when (error) {
            ArtistSettingsError.ARTIST_NOT_FOUND -> {
              logger.warn { "Artist $artistId not found for re-sync: ${error.code}" }
              Response.status(Response.Status.NOT_FOUND)
                .entity(mapOf("error" to messages.playbackErrorArtistNotFound(artistId)))
                .build()
            }
            else -> {
              logger.error { "Artist re-sync failed for $artistId: ${error.code}" }
              Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to messages.playbackErrorArtistResyncFailed(error.code)))
                .build()
            }
          }
        },
        ifRight = { Response.ok(mapOf("status" to "ok")).build() },
      )
    }

  @POST
  @Authenticated
  @Path("/artists/{artistId}/set-sync")
  @Produces(MediaType.APPLICATION_JSON)
  fun setArtistSync(@PathParam("artistId") artistId: String): Response =
    httpResponseMetrics.timed("rest.catalog.artist-set-sync") {
      handleArtistAction(artistId, "set-sync", messages::playbackErrorArtistSetSyncFailed) { catalog.setArtistSync(artistId) }
    }

  @POST
  @Authenticated
  @Path("/artists/{artistId}/set-shallow")
  @Produces(MediaType.APPLICATION_JSON)
  fun setArtistShallow(@PathParam("artistId") artistId: String): Response =
    httpResponseMetrics.timed("rest.catalog.artist-set-shallow") {
      handleArtistAction(artistId, "set-shallow", messages::playbackErrorArtistSetShallowFailed) { catalog.setArtistShallow(artistId) }
    }

  private fun handleArtistAction(
    artistId: String,
    action: String,
    actionFailedMessage: (String) -> String,
    block: () -> Either<DomainError, Unit>,
  ): Response =
    block().fold(
      ifLeft = { error ->
        when (error) {
          ArtistSettingsError.ARTIST_NOT_FOUND -> {
            logger.warn { "Artist $artistId not found for $action: ${error.code}" }
            Response.status(Response.Status.NOT_FOUND)
              .entity(mapOf("error" to messages.playbackErrorArtistNotFound(artistId)))
              .build()
          }
          else -> {
            logger.error { "Artist $action failed for $artistId: ${error.code}" }
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
              .entity(mapOf("error" to actionFailedMessage(error.code)))
              .build()
          }
        }
      },
      ifRight = { Response.ok(mapOf("status" to "ok")).build() },
    )

  companion object : KLogging()
}
