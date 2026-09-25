package de.chrgroth.spotify.control.domain.outbox

import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogPort
import de.chrgroth.spotify.control.domain.port.`in`.catalog.FollowSuggestionsPort
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackAggregationPort
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackPort
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistCheckPort
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistPort
import de.chrgroth.spotify.control.domain.port.`in`.playlist.SingularityPort
import de.chrgroth.spotify.control.domain.port.`in`.playlist.SingularitySuggestionsPort
import de.chrgroth.spotify.control.domain.port.`in`.user.UserProfilePort
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DomainOutboxContractTests {

  private val allEvents: List<DomainOutboxEvent> = listOf(
    DomainOutboxEvent.FetchPlaybackData(),
    DomainOutboxEvent.UpdateUserProfile(),
    DomainOutboxEvent.SyncPlaylistInfo(),
    DomainOutboxEvent.SyncPlaylistData("playlist-1"),
    DomainOutboxEvent.SyncPlaylistData("playlist-1", "https://api.spotify.com/v1/playlists/playlist-1/tracks?offset=50&limit=50", "snapshot-abc"),
    DomainOutboxEvent.RebuildPlaybackData(),
    DomainOutboxEvent.AppendPlaybackData(),
    DomainOutboxEvent.SyncArtistDetails("artist-1"),
    DomainOutboxEvent.SyncArtistDetails("artist-2", fromPlaylist = true),
    DomainOutboxEvent.SyncArtistAlbums("artist-1"),
    DomainOutboxEvent.SyncArtistAlbums("artist-1", "https://api.spotify.com/v1/artists/artist-1/albums?offset=50&limit=50"),
    DomainOutboxEvent.SyncAlbumDetails("album-1"),
    DomainOutboxEvent.SyncFollowedArtists(),
    DomainOutboxEvent.ConfirmArtistSync("artist-1"),
    DomainOutboxEvent.ConfirmArtistShallow("artist-1"),
    DomainOutboxEvent.ResyncCatalog(),
    DomainOutboxEvent.WipeCatalog(),
    DomainOutboxEvent.RunPlaylistChecks("playlist-1"),
    DomainOutboxEvent.RunPlaylistChecks("playlist-1", "duplicate-track-ids"),
    DomainOutboxEvent.FixPlaylistCheck("playlist-1", "duplicate-track-ids"),
    DomainOutboxEvent.FixPlaylistCheck("playlist-1", "duplicate-track-ids", setOf("t1", "t2")),
    DomainOutboxEvent.AggregatePlaybackData(AggregationPeriodType.DAY, LocalDate(2024, 1, 15)),
    DomainOutboxEvent.AggregatePlaybackData(AggregationPeriodType.WEEK, LocalDate(2024, 1, 8)),
    DomainOutboxEvent.AggregatePlaybackData(AggregationPeriodType.MONTH, LocalDate(2024, 1, 1)),
    DomainOutboxEvent.AggregatePlaybackData(AggregationPeriodType.QUARTER, LocalDate(2024, 1, 1)),
    DomainOutboxEvent.AggregatePlaybackData(AggregationPeriodType.YEAR, LocalDate(2024, 1, 1)),
    DomainOutboxEvent.RebuildAllAggregations(),
    DomainOutboxEvent.RebuildFollowSuggestions(),
    DomainOutboxEvent.RebuildSingularitySuggestions(),
    DomainOutboxEvent.AcceptSingularityChallenger("artist-1", "track-1"),
    DomainOutboxEvent.DiscardSingularityChallenger("artist-1", "track-1"),
    DomainOutboxEvent.ReconcileSingularityTracking("playlist-1"),
    DomainOutboxEvent.DetectSingularityChallenger("album-1"),
  )

  @Test
  fun `every DomainOutboxEvent returns a non-blank deduplication key`() {
    allEvents.forEach { event ->
      assertThat(event.deduplicationKey)
        .describedAs("deduplicationKey for ${event::class.simpleName}")
        .isNotBlank()
    }
  }

  @Test
  fun `payload round-trip restores original event`() {
    allEvents.forEach { event ->
      val restored = DomainOutboxEvent.fromKey(event.key, event.serializePayload)
      assertThat(restored)
        .describedAs("round-trip for ${event::class.simpleName}")
        .isEqualTo(event)
    }
  }

  @Test
  fun `SyncArtistDetails parses legacy payload without fromPlaylist as false`() {
    val restored = DomainOutboxEvent.fromKey(DomainOutboxEvent.SyncArtistDetails.KEY, "artist-1")
    assertThat(restored).isEqualTo(DomainOutboxEvent.SyncArtistDetails("artist-1", fromPlaylist = false))
  }

  @Test
  fun `FixPlaylistCheck parses legacy payload without violationIds as empty set`() {
    val restored = DomainOutboxEvent.fromKey(DomainOutboxEvent.FixPlaylistCheck.KEY, "playlist-1\nduplicate-track-ids")
    assertThat(restored).isEqualTo(DomainOutboxEvent.FixPlaylistCheck("playlist-1", "duplicate-track-ids"))
  }

  @Test
  fun `every DomainOutboxEvent in a multi-worker partition carries a non-blank groupId`() {
    allEvents
      .filter { it.partition.workerCount > 1 }
      .forEach { event ->
        assertThat(event.groupId)
          .describedAs("groupId for ${event::class.simpleName} in partition '${event.partition.key}'")
          .isNotNull()
          .isNotBlank()
      }
  }

  @Test
  fun `entity-scoped DomainOutboxEvents use the entity id as groupId`() {
    assertThat(DomainOutboxEvent.SyncArtistDetails("artist-1").groupId).isEqualTo("artist-1")
    assertThat(DomainOutboxEvent.SyncArtistAlbums("artist-1").groupId).isEqualTo("artist-1")
    assertThat(DomainOutboxEvent.SyncAlbumDetails("album-1").groupId).isEqualTo("album-1")
    assertThat(DomainOutboxEvent.ConfirmArtistSync("artist-1").groupId).isEqualTo("artist-1")
    assertThat(DomainOutboxEvent.ConfirmArtistShallow("artist-1").groupId).isEqualTo("artist-1")
    assertThat(DomainOutboxEvent.RunPlaylistChecks("playlist-1").groupId).isEqualTo("playlist-1")
  }

  @Test
  fun `non-entity-scoped domain events in a multi-worker partition fall back to their own key as groupId`() {
    assertThat(DomainOutboxEvent.RebuildPlaybackData().groupId).isEqualTo(DomainOutboxEvent.RebuildPlaybackData.KEY)
    assertThat(DomainOutboxEvent.AppendPlaybackData().groupId).isEqualTo(DomainOutboxEvent.AppendPlaybackData.KEY)
    assertThat(DomainOutboxEvent.AggregatePlaybackData(AggregationPeriodType.DAY, LocalDate(2024, 1, 15)).groupId)
      .isEqualTo(DomainOutboxEvent.AggregatePlaybackData.KEY)
    assertThat(DomainOutboxEvent.RebuildAllAggregations().groupId).isEqualTo(DomainOutboxEvent.RebuildAllAggregations.KEY)
    assertThat(DomainOutboxEvent.ResyncCatalog().groupId).isEqualTo(DomainOutboxEvent.ResyncCatalog.KEY)
    assertThat(DomainOutboxEvent.WipeCatalog().groupId).isEqualTo(DomainOutboxEvent.WipeCatalog.KEY)
    assertThat(DomainOutboxEvent.RebuildPlaylistChecksDashboard().groupId).isEqualTo(DomainOutboxEvent.RebuildPlaylistChecksDashboard.KEY)
    assertThat(DomainOutboxEvent.RebuildPlaylistSettingsView().groupId).isEqualTo(DomainOutboxEvent.RebuildPlaylistSettingsView.KEY)
    assertThat(DomainOutboxEvent.RebuildDashboardReadModel().groupId).isEqualTo(DomainOutboxEvent.RebuildDashboardReadModel.KEY)
    assertThat(DomainOutboxEvent.RebuildFollowSuggestions().groupId).isEqualTo(DomainOutboxEvent.RebuildFollowSuggestions.KEY)
    assertThat(DomainOutboxEvent.RebuildSingularitySuggestions().groupId).isEqualTo(DomainOutboxEvent.RebuildSingularitySuggestions.KEY)
  }

  @Test
  fun `every DomainOutboxEvent type has a handler method in one of the domain ports`() {
    val allPortMethods = listOf(
      PlaybackPort::class, PlaybackAggregationPort::class, CatalogPort::class,
      PlaylistPort::class, PlaylistCheckPort::class, UserProfilePort::class, FollowSuggestionsPort::class, SingularityPort::class,
      SingularitySuggestionsPort::class,
    )
      .flatMap { it.java.methods.toList() }
    allEvents.forEach { event ->
      val eventClass = event::class.java
      val hasMatchingHandle = allPortMethods.any { method ->
        method.name == "handle" && method.parameterCount == 1 && method.parameterTypes[0].isAssignableFrom(eventClass)
      }
      assertThat(hasMatchingHandle)
        .describedAs("One of the domain ports should have method 'handle(${eventClass.simpleName})'")
        .isTrue()
    }
  }
}
