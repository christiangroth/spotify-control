package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.FollowCandidate
import de.chrgroth.spotify.control.domain.model.catalog.FollowSuggestionsView
import de.chrgroth.spotify.control.domain.model.catalog.UnfollowCandidate
import de.chrgroth.spotify.control.domain.port.out.readmodel.FollowSuggestionsViewRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlin.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@QuarkusTest
class FollowSuggestionsViewRepositoryTests {

  @Inject
  lateinit var followSuggestionsViewRepository: FollowSuggestionsViewRepositoryPort

  @Inject
  lateinit var followSuggestionsViewDocumentRepository: FollowSuggestionsViewDocumentRepository

  @BeforeEach
  fun cleanUp() {
    followSuggestionsViewDocumentRepository.deleteAll()
  }

  private fun buildView() = FollowSuggestionsView(
    followCandidates = listOf(
      FollowCandidate(
        artistId = ArtistId("artist-1"),
        artistName = "Follow Candidate",
        imageLink = "https://example.org/artist-1.jpg",
        recentPlaybackSeconds = 300,
        topRankWeeks = 2,
      ),
    ),
    unfollowCandidates = listOf(
      UnfollowCandidate(
        artistId = ArtistId("artist-2"),
        artistName = "Unfollow Candidate",
        imageLink = "https://example.org/artist-2.jpg",
        followedSince = Instant.fromEpochSeconds(0),
        lookbackPlaybackSeconds = 0,
      ),
    ),
  )

  @Test
  fun `find returns null when no view has been saved yet`() {
    assertThat(followSuggestionsViewRepository.find()).isNull()
  }

  @Test
  fun `save and find round-trips the follow suggestions view`() {
    val view = buildView()

    followSuggestionsViewRepository.save(view)

    assertThat(followSuggestionsViewRepository.find()).isEqualTo(view)
  }

  @Test
  fun `save overwrites the previously stored follow suggestions view`() {
    followSuggestionsViewRepository.save(buildView())

    val updated = FollowSuggestionsView()
    followSuggestionsViewRepository.save(updated)

    assertThat(followSuggestionsViewRepository.find()).isEqualTo(updated)
  }
}
