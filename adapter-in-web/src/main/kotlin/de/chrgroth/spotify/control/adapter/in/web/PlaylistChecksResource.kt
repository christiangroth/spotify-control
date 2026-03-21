package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.model.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.AppPlaylistCheckRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.MongoReadTimeoutPort
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
    private lateinit var readTimeout: MongoReadTimeoutPort

    @GET
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    fun playlistChecks(): TemplateInstance {
        val userId = UserId(securityIdentity.principal.name)
        val contextClassLoader = Thread.currentThread().contextClassLoader
        val (user, playlistNameById, checks) = runBlocking {
            val userAsync = async(Dispatchers.IO) {
                Thread.currentThread().contextClassLoader = contextClassLoader
                readTimeout.timedWithFallback("userRepository.findById", null) { userRepository.findById(userId) }
            }
            val playlistsAsync = async(Dispatchers.IO) {
                Thread.currentThread().contextClassLoader = contextClassLoader
                readTimeout.timedWithFallback("playlistRepository.findByUserId", emptyList()) {
                    playlistRepository.findByUserId(userId)
                }
            }
            val checksAsync = async(Dispatchers.IO) {
                Thread.currentThread().contextClassLoader = contextClassLoader
                readTimeout.timedWithFallback("playlistCheckRepository.findAll", emptyList<AppPlaylistCheck>()) {
                    playlistCheckRepository.findAll()
                }
            }
            Triple(userAsync.await(), playlistsAsync.await().associateBy({ it.spotifyPlaylistId }, { it.name }), checksAsync.await())
        }
        val groups = checks
            .map { check ->
                PlaylistCheckRow(
                    check = check,
                    playlistName = playlistNameById[check.playlistId] ?: check.playlistId,
                )
            }
            .groupBy { it.check.checkId.substringAfterLast(":") }
            .map { (checkType, rows) -> PlaylistCheckGroup(PlaylistCheckRow.formatCheckName(checkType), rows.sortedBy { it.playlistName }) }
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

            fun formatCheckName(checkType: String): String = checkType
                .split("-")
                .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
        }
    }
}
