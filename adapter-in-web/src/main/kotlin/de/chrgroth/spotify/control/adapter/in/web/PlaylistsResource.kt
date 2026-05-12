package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistCheckPort
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistPort
import de.chrgroth.spotify.control.domain.port.`in`.user.UserProfilePort
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

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
  private val userRepository: UserRepositoryPort,
  private val playlistRepository: PlaylistRepositoryPort,
  private val playlistCheckRepository: AppPlaylistCheckRepositoryPort,
  private val playlistCheckPort: PlaylistCheckPort,
) {

  @GET
  @Path("/settings")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun settings(): TemplateInstance {
    val userId = UserId(securityIdentity.principal.name)
    val displayName = userProfile.getDisplayName(userId) ?: userId.value
    val sortedPlaylists = playlist.getPlaylists(userId).sortedBy { it.name }
    val padWidth = sortedPlaylists.size.toString().length
    val trackCounts = playlist.getTrackCounts(userId)
    val rows = sortedPlaylists.mapIndexed { index, playlistInfo ->
      PlaylistSettingsResource.PlaylistRow(
        lineNumber = (index + 1).toString().padStart(padWidth, '0'),
        playlist = playlistInfo,
        numberOfTracks = trackCounts[playlistInfo.spotifyPlaylistId],
      )
    }
    return playlistTemplate
      .data("displayName", displayName)
      .data("rows", rows)
  }

  @GET
  @Path("/checks")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun checks(): TemplateInstance {
    val userId = UserId(securityIdentity.principal.name)
    val (user, playlistNameById, checks) = runBlocking {
      val userAsync = async(Dispatchers.IO) { userRepository.findById(userId) }
      val playlistNamesAsync = async(Dispatchers.IO) {
        playlistRepository.findByUserId(userId).associateBy({ it.spotifyPlaylistId }, { it.name })
      }
      val checksAsync = async(Dispatchers.IO) { playlistCheckRepository.findAll() }
      Triple(userAsync.await(), playlistNamesAsync.await(), checksAsync.await())
    }
    val displayNames = playlistCheckPort.getDisplayNames()
    val fixableCheckIds = playlistCheckPort.getFixableCheckIds()
    val groups = checks
      .map { check ->
        PlaylistChecksResource.PlaylistCheckRow(
          check = check,
          playlistName = playlistNameById[check.playlistId.value] ?: check.playlistId.value,
          hasfix = fixableCheckIds.contains(check.checkId.substringAfterLast(":")),
        )
      }
      .groupBy { it.check.checkId.substringAfterLast(":") }
      .map { (checkType, rows) ->
        val name = displayNames[checkType] ?: checkType
        PlaylistChecksResource.PlaylistCheckGroup(name, rows.sortedBy { it.playlistName })
      }
      .sortedBy { it.checkName }
    return playlistChecksTemplate
      .data("displayName", user?.displayName ?: userId.value)
      .data("groups", groups)
  }
}
