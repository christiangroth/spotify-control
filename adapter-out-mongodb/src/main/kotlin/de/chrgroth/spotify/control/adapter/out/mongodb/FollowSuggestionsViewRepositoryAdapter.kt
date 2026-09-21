package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.adapter.out.mongodb.MongoQueryMetrics.Companion.SINGLETON_ID
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.FollowCandidate
import de.chrgroth.spotify.control.domain.model.catalog.FollowSuggestionsView
import de.chrgroth.spotify.control.domain.model.catalog.UnfollowCandidate
import de.chrgroth.spotify.control.domain.port.out.readmodel.FollowSuggestionsViewRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

@ApplicationScoped
class FollowSuggestionsViewRepositoryAdapter(
  private val followSuggestionsViewDocumentRepository: FollowSuggestionsViewDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : FollowSuggestionsViewRepositoryPort {

  override fun save(view: FollowSuggestionsView) {
    mongoQueryMetrics.saveSingleton(followSuggestionsViewDocumentRepository, "app_follow_suggestions_view", view.toDocument())
  }

  override fun find(): FollowSuggestionsView? =
    mongoQueryMetrics.findSingleton(followSuggestionsViewDocumentRepository, "app_follow_suggestions_view")?.toDomain()

  private fun FollowSuggestionsViewDocument.toDomain() = FollowSuggestionsView(
    followCandidates = followCandidates.map { candidate ->
      FollowCandidate(
        artistId = ArtistId(candidate.artistId),
        artistName = candidate.artistName,
        imageLink = candidate.imageLink,
        recentPlaybackSeconds = candidate.recentPlaybackSeconds,
        topRankWeeks = candidate.topRankWeeks,
      )
    },
    unfollowCandidates = unfollowCandidates.map { candidate ->
      UnfollowCandidate(
        artistId = ArtistId(candidate.artistId),
        artistName = candidate.artistName,
        imageLink = candidate.imageLink,
        followedSince = candidate.followedSince?.toKotlinInstant(),
        lookbackPlaybackSeconds = candidate.lookbackPlaybackSeconds,
      )
    },
  )

  private fun FollowSuggestionsView.toDocument() = FollowSuggestionsViewDocument().apply {
    id = SINGLETON_ID
    followCandidates = this@toDocument.followCandidates.map { candidate ->
      FollowCandidateDocument().apply {
        artistId = candidate.artistId.value
        artistName = candidate.artistName
        imageLink = candidate.imageLink
        recentPlaybackSeconds = candidate.recentPlaybackSeconds
        topRankWeeks = candidate.topRankWeeks
      }
    }
    unfollowCandidates = this@toDocument.unfollowCandidates.map { candidate ->
      UnfollowCandidateDocument().apply {
        artistId = candidate.artistId.value
        artistName = candidate.artistName
        imageLink = candidate.imageLink
        followedSince = candidate.followedSince?.toJavaInstant()
        lookbackPlaybackSeconds = candidate.lookbackPlaybackSeconds
      }
    }
  }
}
