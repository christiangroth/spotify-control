package de.chrgroth.spotify.control.domain.outbox

import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.CatalogPort
import de.chrgroth.spotify.control.domain.port.`in`.PlaybackPort
import de.chrgroth.spotify.control.domain.port.`in`.PlaylistPort
import de.chrgroth.spotify.control.domain.port.`in`.UserProfilePort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DomainOutboxContractTests {

    private val allEvents: List<DomainOutboxEvent> = listOf(
        DomainOutboxEvent.FetchCurrentlyPlaying(UserId("user-1")),
        DomainOutboxEvent.FetchRecentlyPlayed(UserId("user-1")),
        DomainOutboxEvent.UpdateUserProfile(UserId("user-1")),
        DomainOutboxEvent.SyncPlaylistInfo(UserId("user-1")),
        DomainOutboxEvent.SyncPlaylistData(UserId("user-1"), "playlist-1"),
        DomainOutboxEvent.RebuildPlaybackData(UserId("user-1")),
        DomainOutboxEvent.AppendPlaybackData(UserId("user-1")),
        DomainOutboxEvent.EnrichArtistDetails("artist-1", UserId("user-1")),
        DomainOutboxEvent.EnrichTrackDetails("track-1", UserId("user-1")),
    )

    @Test
    fun `every DomainOutboxEvent returns a non-blank deduplication key`() {
        allEvents.forEach { event ->
            assertThat(event.deduplicationKey())
                .describedAs("deduplicationKey for ${event::class.simpleName}")
                .isNotBlank()
        }
    }

    @Test
    fun `deduplication key includes userId to allow per-user deduplication`() {
        val userId = "user-abc"
        listOf(
            DomainOutboxEvent.FetchCurrentlyPlaying(UserId(userId)),
            DomainOutboxEvent.FetchRecentlyPlayed(UserId(userId)),
            DomainOutboxEvent.UpdateUserProfile(UserId(userId)),
            DomainOutboxEvent.SyncPlaylistInfo(UserId(userId)),
            DomainOutboxEvent.SyncPlaylistData(UserId(userId), "playlist-abc"),
            DomainOutboxEvent.RebuildPlaybackData(UserId(userId)),
            DomainOutboxEvent.AppendPlaybackData(UserId(userId)),
        ).forEach { event ->
            assertThat(event.deduplicationKey())
                .describedAs("deduplicationKey for ${event::class.simpleName} should contain userId")
                .contains(userId)
        }
    }

    @Test
    fun `payload round-trip restores original event`() {
        allEvents.forEach { event ->
            val restored = DomainOutboxEvent.fromKey(event.key, event.toPayload())
            assertThat(restored)
                .describedAs("round-trip for ${event::class.simpleName}")
                .isEqualTo(event)
        }
    }

    @Test
    fun `every DomainOutboxEvent type has a handler method in one of the domain ports`() {
        val allPortMethods = listOf(PlaybackPort::class, CatalogPort::class, PlaylistPort::class, UserProfilePort::class)
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

    @Test
    fun `ToSpotify partition has no throttle interval`() {
        assertThat(DomainOutboxPartition.ToSpotify.pauseOnRateLimit).isTrue()
    }

    @Test
    fun `ToSpotifyPlayback partition does not pause on rate limit`() {
        assertThat(DomainOutboxPartition.ToSpotifyPlayback.pauseOnRateLimit).isFalse()
    }

    @Test
    fun `Domain partition does not pause on rate limit`() {
        assertThat(DomainOutboxPartition.Domain.pauseOnRateLimit).isFalse()
    }
}
