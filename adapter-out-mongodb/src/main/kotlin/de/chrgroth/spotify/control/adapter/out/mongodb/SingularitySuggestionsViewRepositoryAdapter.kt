package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.adapter.out.mongodb.MongoQueryMetrics.Companion.SINGLETON_ID
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.playlist.SingularityExcludeCandidate
import de.chrgroth.spotify.control.domain.model.playlist.SingularityIncludeCandidate
import de.chrgroth.spotify.control.domain.model.playlist.SingularitySuggestionsView
import de.chrgroth.spotify.control.domain.port.out.readmodel.SingularitySuggestionsViewRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

@ApplicationScoped
class SingularitySuggestionsViewRepositoryAdapter(
  private val singularitySuggestionsViewDocumentRepository: SingularitySuggestionsViewDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : SingularitySuggestionsViewRepositoryPort {

  override fun save(view: SingularitySuggestionsView) {
    mongoQueryMetrics.saveSingleton(singularitySuggestionsViewDocumentRepository, "app_singularity_suggestions_view", view.toDocument())
  }

  override fun find(): SingularitySuggestionsView? =
    mongoQueryMetrics.findSingleton(singularitySuggestionsViewDocumentRepository, "app_singularity_suggestions_view")?.toDomain()

  private fun SingularitySuggestionsViewDocument.toDomain() = SingularitySuggestionsView(
    includeCandidates = includeCandidates.map { candidate ->
      SingularityIncludeCandidate(
        artistId = ArtistId(candidate.artistId),
        artistName = candidate.artistName,
        imageLink = candidate.imageLink,
        recentPlaybackSeconds = candidate.recentPlaybackSeconds,
        topRankWeeks = candidate.topRankWeeks,
      )
    },
    excludeCandidates = excludeCandidates.map { candidate ->
      SingularityExcludeCandidate(
        artistId = ArtistId(candidate.artistId),
        artistName = candidate.artistName,
        imageLink = candidate.imageLink,
        currentTrackAddedAt = candidate.currentTrackAddedAt?.toKotlinInstant(),
        lookbackPlaybackSeconds = candidate.lookbackPlaybackSeconds,
      )
    },
  )

  private fun SingularitySuggestionsView.toDocument() = SingularitySuggestionsViewDocument().apply {
    id = SINGLETON_ID
    includeCandidates = this@toDocument.includeCandidates.map { candidate ->
      SingularityIncludeCandidateDocument().apply {
        artistId = candidate.artistId.value
        artistName = candidate.artistName
        imageLink = candidate.imageLink
        recentPlaybackSeconds = candidate.recentPlaybackSeconds
        topRankWeeks = candidate.topRankWeeks
      }
    }
    excludeCandidates = this@toDocument.excludeCandidates.map { candidate ->
      SingularityExcludeCandidateDocument().apply {
        artistId = candidate.artistId.value
        artistName = candidate.artistName
        imageLink = candidate.imageLink
        currentTrackAddedAt = candidate.currentTrackAddedAt?.toJavaInstant()
        lookbackPlaybackSeconds = candidate.lookbackPlaybackSeconds
      }
    }
  }
}
