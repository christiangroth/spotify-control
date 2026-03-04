package de.chrgroth.spotify.control.domain.outbox

import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.`in`.OutboxHandlerPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DomainOutboxContractTests {

    private val allEvents: List<DomainOutboxEvent> = listOf(
        DomainOutboxEvent.FetchRecentlyPlayed(UserId("user-1")),
        DomainOutboxEvent.UpdateUserProfile(UserId("user-1")),
        DomainOutboxEvent.SyncPlaylistInfo(UserId("user-1")),
        DomainOutboxEvent.SyncPlaylistData(UserId("user-1"), "playlist-1"),
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
            DomainOutboxEvent.FetchRecentlyPlayed(UserId(userId)),
            DomainOutboxEvent.UpdateUserProfile(UserId(userId)),
            DomainOutboxEvent.SyncPlaylistInfo(UserId(userId)),
            DomainOutboxEvent.SyncPlaylistData(UserId(userId), "playlist-abc"),
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
    fun `OutboxHandlerPort has a handler method for every DomainOutboxEvent type`() {
        val methods = OutboxHandlerPort::class.java.methods
        allEvents.forEach { event ->
            val eventClass = event::class.java
            val hasMatchingHandle = methods.any { method ->
                method.name == "handle" && method.parameterCount == 1 && method.parameterTypes[0].isAssignableFrom(eventClass)
            }
            assertThat(hasMatchingHandle)
                .describedAs("OutboxHandlerPort should have method 'handle(${eventClass.simpleName})'")
                .isTrue()
        }
    }
}
