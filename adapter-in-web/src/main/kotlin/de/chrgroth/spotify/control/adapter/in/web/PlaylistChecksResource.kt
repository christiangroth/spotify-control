package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.model.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
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

    @GET
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    fun playlistChecks(): TemplateInstance {
        val userId = UserId(securityIdentity.principal.name)
        val user = userRepository.findById(userId)
        val playlistNameById = playlistRepository.findByUserId(userId).associateBy({ it.spotifyPlaylistId }, { it.name })
        val checks = playlistCheckRepository.findAll()
        val rows = checks
            .sortedWith(compareBy({ it.succeeded }, { it.playlistId }))
            .map { check ->
                PlaylistCheckRow(
                    check = check,
                    playlistName = playlistNameById[check.playlistId] ?: check.playlistId,
                )
            }
        return playlistChecksTemplate
            .data("displayName", user?.displayName ?: userId.value)
            .data("rows", rows)
    }

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
