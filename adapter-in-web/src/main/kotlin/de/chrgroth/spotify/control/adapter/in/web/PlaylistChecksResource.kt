package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistCheckPort
import de.chrgroth.spotify.control.domain.port.out.playlist.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

@Path("/playlist-checks")
@ApplicationScoped
@Suppress("Unused")
class PlaylistChecksResource {

    @Inject
    @Location("playlist-checks.html")
    private lateinit var playlistChecksTemplate: Template

    @Inject
    private lateinit var securityIdentity: SecurityIdentity

    @Inject
    private lateinit var userRepository: UserRepositoryPort

    @Inject
    private lateinit var playlistRepository: PlaylistRepositoryPort

    @Inject
    private lateinit var playlistCheckRepository: AppPlaylistCheckRepositoryPort

    @Inject
    private lateinit var playlistCheckPort: PlaylistCheckPort

    @GET
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    fun playlistChecks(): TemplateInstance {
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
        val groups = checks
            .map { check ->
                PlaylistCheckRow(
                    check = check,
                    playlistName = playlistNameById[check.playlistId.value] ?: check.playlistId.value,
                )
            }
            .groupBy { it.check.checkId.substringAfterLast(":") }
            .map { (checkType, rows) ->
                val name = displayNames[checkType] ?: checkType
                PlaylistCheckGroup(name, rows.sortedBy { it.playlistName })
            }
            .sortedBy { it.checkName }
        return playlistChecksTemplate
            .data("displayName", user?.displayName ?: userId.value)
            .data("groups", groups)
    }

    data class PlaylistCheckGroup(val checkName: String, val rows: List<PlaylistCheckRow>)

    data class PlaylistCheckRow(val check: AppPlaylistCheck, val playlistName: String) {
        val checkDateFormatted: String get() = check.lastCheck
            .toJavaInstant()
            .atZone(ZoneId.systemDefault())
            .format(DATE_TIME_FORMATTER)

        companion object {
            private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMAN)
        }
    }
}
