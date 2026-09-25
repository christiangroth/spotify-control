package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.port.`in`.playlist.SingularitySuggestionsPort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import kotlin.time.Instant

@Path("/playlists/singularity-suggestions")
@ApplicationScoped
@Suppress("Unused")
class SingularitySuggestionsResource(
  private val singularitySuggestions: SingularitySuggestionsPort,
  private val httpResponseMetrics: ResponseTimingPort,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun page(): TemplateInstance = httpResponseMetrics.timed("page.playlist.singularity-suggestions") {
    val view = singularitySuggestions.getSingularitySuggestionsView()
    val includeRows = view.includeCandidates.map { candidate ->
      IncludeCandidateRow(
        artistId = candidate.artistId.value,
        name = candidate.artistName,
        imageLink = candidate.imageLink,
        totalSeconds = candidate.recentPlaybackSeconds,
        topRankWeeks = candidate.topRankWeeks,
      )
    }
    val excludeRows = view.excludeCandidates.map { candidate ->
      ExcludeCandidateRow(
        artistId = candidate.artistId.value,
        name = candidate.artistName,
        imageLink = candidate.imageLink,
        totalSeconds = candidate.lookbackPlaybackSeconds,
        currentTrackAddedAt = candidate.currentTrackAddedAt,
      )
    }
    Templates.`singularity-suggestions`(includeRows, excludeRows)
  }

  // trackDurationMs is unused for kind='artist' but must exist since tags/catalog-rank-entry.html
  // unconditionally evaluates `entry.trackDurationMs` regardless of kind.
  data class IncludeCandidateRow(
    val artistId: String,
    val name: String,
    val imageLink: String?,
    val totalSeconds: Long,
    val topRankWeeks: Int,
    val trackDurationMs: Long? = null,
  )

  data class ExcludeCandidateRow(
    val artistId: String,
    val name: String,
    val imageLink: String?,
    val totalSeconds: Long,
    val currentTrackAddedAt: Instant?,
    val trackDurationMs: Long? = null,
  )
}
