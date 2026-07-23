package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.error.PlaylistSyncError
import mu.KLogging
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistPort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/settings/playlist")
@ApplicationScoped
@Suppress("Unused")
class PlaylistSettingsResource(
  private val playlist: PlaylistPort,
  private val httpResponseMetrics: ResponseTimingPort,
) {

  @PUT
  @Authenticated
  @Path("/{playlistId}/sync-status")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun updateSyncStatus(
    @PathParam("playlistId") playlistId: String,
    request: SyncStatusRequest,
  ): Response = httpResponseMetrics.timed("rest.playlist.sync-status-update") {
    val syncStatus = PlaylistSyncStatus.entries.find { it.name == request.syncStatus }
      ?: return@timed Response.status(Response.Status.BAD_REQUEST)
        .entity(mapOf("error" to "Invalid sync status: ${request.syncStatus}"))
        .build()
    when (playlist.updateSyncStatus(playlistId, syncStatus).isRight()) {
      true -> {
        val updated = playlist.getPlaylists().find { it.spotifyPlaylistId == playlistId }
        Response.ok(mapOf("syncStatus" to syncStatus.name, "type" to updated?.type?.name)).build()
      }
      false -> {
        logger.warn { "Playlist $playlistId not found during sync status update" }
        Response.status(Response.Status.NOT_FOUND)
          .entity(mapOf("error" to "Playlist not found"))
          .build()
      }
    }
  }

  data class SyncStatusRequest(val syncStatus: String = "")

  @PUT
  @Authenticated
  @Path("/{playlistId}/type")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun updatePlaylistType(
    @PathParam("playlistId") playlistId: String,
    request: PlaylistTypeRequest,
  ): Response = httpResponseMetrics.timed("rest.playlist.type-update") {
    val type = PlaylistType.entries.find { it.name == request.type }
      ?: return@timed Response.status(Response.Status.BAD_REQUEST)
        .entity(mapOf("error" to "Invalid playlist type: ${request.type}"))
        .build()
    playlist.updatePlaylistType(playlistId, type).fold(
      ifLeft = { error ->
        when (error) {
          PlaylistSyncError.PLAYLIST_TYPE_CONFLICT -> {
            logger.warn { "Playlist type update conflict for $playlistId: ${error.code}" }
            Response.status(Response.Status.CONFLICT)
              .entity(mapOf("error" to "Only one playlist of type ALL is allowed"))
              .build()
          }
          PlaylistSyncError.PLAYLIST_NOT_ACTIVE -> {
            logger.warn { "Playlist type update rejected for inactive playlist $playlistId: ${error.code}" }
            Response.status(Response.Status.BAD_REQUEST)
              .entity(mapOf("error" to "Playlist type can only be set for active playlists"))
              .build()
          }
          else -> {
            logger.warn { "Playlist $playlistId not found during type update: ${error.code}" }
            Response.status(Response.Status.NOT_FOUND)
              .entity(mapOf("error" to "Playlist not found"))
              .build()
          }
        }
      },
      ifRight = { Response.ok(mapOf("type" to type.name)).build() },
    )
  }

  data class PlaylistTypeRequest(val type: String = "")

  @POST
  @Authenticated
  @Path("/sync")
  @Produces(MediaType.APPLICATION_JSON)
  fun syncNow(): Response = httpResponseMetrics.timed("rest.playlist.sync-now") {
    playlist.enqueueUpdates()
    Response.ok(mapOf("status" to "ok")).build()
  }

  @POST
  @Authenticated
  @Path("/{playlistId}/sync")
  @Produces(MediaType.APPLICATION_JSON)
  fun syncPlaylist(@PathParam("playlistId") playlistId: String): Response = httpResponseMetrics.timed("rest.playlist.sync-one") {
    playlist.enqueueSyncPlaylistData(playlistId).fold(
      ifLeft = { error ->
        when (error) {
          PlaylistSyncError.PLAYLIST_SYNC_INACTIVE -> {
            logger.warn { "Sync enqueue rejected for inactive playlist $playlistId: ${error.code}" }
            Response.status(Response.Status.BAD_REQUEST)
              .entity(mapOf("error" to "Sync enqueue failed: ${error.code}"))
              .build()
          }
          else -> {
            logger.warn { "Sync enqueue failed for playlist $playlistId: ${error.code}" }
            Response.status(Response.Status.NOT_FOUND)
              .entity(mapOf("error" to "Sync enqueue failed: ${error.code}"))
              .build()
          }
        }
      },
      ifRight = { Response.ok(mapOf("status" to "ok")).build() },
    )
  }

  companion object : KLogging()
}
