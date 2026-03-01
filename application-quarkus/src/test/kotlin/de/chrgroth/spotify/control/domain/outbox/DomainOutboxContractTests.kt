package de.chrgroth.spotify.control.domain.outbox

import de.chrgroth.spotify.control.domain.port.`in`.OutboxHandlerPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DomainOutboxContractTests {

    private val allEvents: List<DomainOutboxEvent> = listOf(
        DomainOutboxEvent.FetchRecentlyPlayed("user-1"),
        DomainOutboxEvent.UpdateUserProfile("user-1"),
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
            DomainOutboxEvent.FetchRecentlyPlayed(userId),
            DomainOutboxEvent.UpdateUserProfile(userId),
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
        val handlerMethodNames = OutboxHandlerPort::class.java.methods.map { it.name }.toSet()
        allEvents.forEach { event ->
            assertThat(handlerMethodNames)
                .describedAs("OutboxHandlerPort should have method 'handle' for event ${event::class.simpleName}")
                .contains("handle")
        }
    }
}
