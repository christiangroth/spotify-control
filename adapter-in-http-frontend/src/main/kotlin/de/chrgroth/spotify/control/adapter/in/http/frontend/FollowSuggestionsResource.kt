package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.port.`in`.catalog.FollowSuggestionsPort
import de.chrgroth.spotify.control.domain.port.out.infra.ResponseTimingPort
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import kotlin.time.Instant

@Path("/follow-suggestions")
@ApplicationScoped
@Suppress("Unused")
class FollowSuggestionsResource(
  private val followSuggestions: FollowSuggestionsPort,
  private val httpResponseMetrics: ResponseTimingPort,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun page(): TemplateInstance = httpResponseMetrics.timed("page.follow-suggestions.view") {
    val view = followSuggestions.getFollowSuggestionsView()
    val followRows = view.followCandidates.map { candidate ->
      FollowCandidateRow(
        artistId = candidate.artistId.value,
        name = candidate.artistName,
        imageLink = candidate.imageLink,
        totalSeconds = candidate.recentPlaybackSeconds,
        topRankWeeks = candidate.topRankWeeks,
      )
    }
    val unfollowRows = view.unfollowCandidates.map { candidate ->
      UnfollowCandidateRow(
        artistId = candidate.artistId.value,
        name = candidate.artistName,
        imageLink = candidate.imageLink,
        totalSeconds = candidate.lookbackPlaybackSeconds,
        followedSince = candidate.followedSince,
      )
    }
    SettingsTemplates.`follow-suggestions`(followRows, unfollowRows)
  }

  // trackDurationMs is unused for kind='artist' but must exist since tags/catalog-rank-entry.html
  // unconditionally evaluates `entry.trackDurationMs` regardless of kind.
  data class FollowCandidateRow(
    val artistId: String,
    val name: String,
    val imageLink: String?,
    val totalSeconds: Long,
    val topRankWeeks: Int,
    val trackDurationMs: Long? = null,
  )

  data class UnfollowCandidateRow(
    val artistId: String,
    val name: String,
    val imageLink: String?,
    val totalSeconds: Long,
    val followedSince: Instant?,
    val trackDurationMs: Long? = null,
  )
}
