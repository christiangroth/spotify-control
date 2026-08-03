package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistCheckPort
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistPort
import de.chrgroth.spotify.control.domain.port.`in`.user.UserProfilePort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import kotlin.time.Instant

@Path("/playlists")
@ApplicationScoped
@Suppress("Unused")
class PlaylistsResource(
  @param:Location("settings/playlist.html")
  private val playlistTemplate: Template,
  @param:Location("playlist-checks.html")
  private val playlistChecksTemplate: Template,
  private val securityIdentity: SecurityIdentity,
  private val userProfile: UserProfilePort,
  private val playlist: PlaylistPort,
  private val playlistCheckPort: PlaylistCheckPort,
  private val httpResponseMetrics: ResponseTimingPort,
) {

  @GET
  @Path("/settings")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun settings(): TemplateInstance = httpResponseMetrics.timed("page.playlist.settings-tab") {
    val displayName = userProfile.getDisplayName() ?: securityIdentity.principal.name
    val sortedEntries = playlist.getPlaylistSettingsView().entries.sortedBy { it.playlist.name }
    val padWidth = sortedEntries.size.toString().length
    val rows = sortedEntries.mapIndexed { index, entry ->
      PlaylistRow(
        lineNumber = (index + 1).toString().padStart(padWidth, '0'),
        playlist = entry.playlist,
        numberOfTracks = entry.numberOfTracks,
        numberOfArtists = entry.numberOfArtists,
        numberOfMissingArtists = entry.numberOfMissingArtists,
      )
    }
    playlistTemplate
      .data("displayName", displayName)
      .data("rows", rows)
  }

  data class PlaylistRow(
    val lineNumber: String,
    val playlist: PlaylistInfo,
    val numberOfTracks: Int? = null,
    val numberOfArtists: Int? = null,
    val numberOfMissingArtists: Int? = null,
  ) {
    val active: Boolean get() = playlist.syncStatus == PlaylistSyncStatus.ACTIVE
    val lastSyncTime: Instant get() = playlist.lastSyncTime ?: playlist.lastSnapshotIdSyncTime
    val typeLabel: String? get() = playlist.type?.name?.lowercase()
  }

  @GET
  @Path("/checks")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun checks(): TemplateInstance = httpResponseMetrics.timed("page.playlist.checks-tab") {
    val dashboard = playlistCheckPort.getCheckDashboard()
    val groups = dashboard.checks
      .map { check ->
        PlaylistChecksResource.PlaylistCheckRow(
          check = check,
          playlistName = dashboard.playlistNameById[check.playlistId.value] ?: check.playlistId.value,
          hasfix = dashboard.fixableCheckIds.contains(check.checkId.substringAfterLast(":")),
        )
      }
      .groupBy { it.check.checkId.substringAfterLast(":") }
      .map { (checkType, rows) ->
        val name = dashboard.displayNames[checkType] ?: checkType
        PlaylistChecksResource.PlaylistCheckGroup(name, checkType, rows.sortedBy { it.playlistName })
      }
      .sortedBy { it.checkName }
    playlistChecksTemplate
      .data("displayName", dashboard.displayName.ifEmpty { securityIdentity.principal.name })
      .data("groups", groups)
  }
}
