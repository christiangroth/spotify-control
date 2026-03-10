package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.error.PlaylistSyncError
import de.chrgroth.spotify.control.domain.model.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.PlaylistPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.toJavaInstant

@Path("/settings/playlist")
@ApplicationScoped
@Suppress("Unused")
class PlaylistSettingsResource {

  @Inject
  @Location("settings/playlist.html")
  private lateinit var playlistTemplate: Template

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  @Inject
  private lateinit var userRepository: UserRepositoryPort

  @Inject
  private lateinit var playlistRepository: PlaylistRepositoryPort

  @Inject
  private lateinit var playlist: PlaylistPort

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun playlist(): TemplateInstance {
    val userId = UserId(securityIdentity.principal.name)
    val user = userRepository.findById(userId)
    val sortedPlaylists = playlistRepository.findByUserId(userId).sortedBy { it.name }
    val padWidth = sortedPlaylists.size.toString().length
    val rows = sortedPlaylists.mapIndexed { index, playlist ->
      PlaylistRow(
        lineNumber = (index + 1).toString().padStart(padWidth, '0'),
        playlist = playlist,
      )
    }
    return playlistTemplate
      .data("displayName", user?.displayName ?: userId.value)
      .data("rows", rows)
  }

  data class PlaylistRow(val lineNumber: String, val playlist: PlaylistInfo) {
    val active: Boolean get() = playlist.syncStatus == PlaylistSyncStatus.ACTIVE
    val lastSnapshotIdSyncTimeFormatted: String get() = playlist.lastSnapshotIdSyncTime
      .toJavaInstant()
      .atZone(ZoneId.systemDefault())
      .format(GERMAN_DATE_TIME_FORMATTER)

    companion object {
      private val GERMAN_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMAN)
    }
  }

  @PUT
  @Authenticated
  @Path("/{playlistId}/sync-status")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun updateSyncStatus(
    @PathParam("playlistId") playlistId: String,
    request: SyncStatusRequest,
  ): Response {
    val userId = UserId(securityIdentity.principal.name)
    val syncStatus = PlaylistSyncStatus.entries.find { it.name == request.syncStatus }
      ?: return Response.status(Response.Status.BAD_REQUEST)
        .entity(mapOf("error" to "Invalid sync status: ${request.syncStatus}"))
        .build()
    return when (playlist.updateSyncStatus(userId, playlistId, syncStatus).isRight()) {
      true -> Response.ok(mapOf("syncStatus" to syncStatus.name)).build()
      false -> Response.status(Response.Status.NOT_FOUND)
        .entity(mapOf("error" to "Playlist not found"))
        .build()
    }
  }

  data class SyncStatusRequest(val syncStatus: String = "")

  @POST
  @Authenticated
  @Path("/sync")
  @Produces(MediaType.APPLICATION_JSON)
  fun syncNow(): Response {
    val userId = UserId(securityIdentity.principal.name)
    return playlist.syncPlaylists(userId).fold(
      ifLeft = { error ->
        Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(mapOf("error" to "Sync failed: ${error.code}"))
          .build()
      },
      ifRight = { Response.ok(mapOf("status" to "ok")).build() },
    )
  }

  @POST
  @Authenticated
  @Path("/{playlistId}/sync")
  @Produces(MediaType.APPLICATION_JSON)
  fun syncPlaylist(@PathParam("playlistId") playlistId: String): Response {
    val userId = UserId(securityIdentity.principal.name)
    return playlist.enqueueSyncPlaylistData(userId, playlistId).fold(
      ifLeft = { error ->
        when (error) {
          PlaylistSyncError.PLAYLIST_SYNC_INACTIVE -> Response.status(Response.Status.BAD_REQUEST)
            .entity(mapOf("error" to "Sync enqueue failed: ${error.code}"))
            .build()
          else -> Response.status(Response.Status.NOT_FOUND)
            .entity(mapOf("error" to "Sync enqueue failed: ${error.code}"))
            .build()
        }
      },
      ifRight = { Response.ok(mapOf("status" to "ok")).build() },
    )
  }
}
