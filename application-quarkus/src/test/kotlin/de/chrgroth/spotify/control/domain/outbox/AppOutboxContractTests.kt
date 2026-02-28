package de.chrgroth.spotify.control.domain.outbox

import de.chrgroth.spotify.control.domain.port.`in`.OutboxHandlerPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AppOutboxContractTests {

    private val allEvents: List<AppOutboxEvent> = listOf(
        AppOutboxEvent.FetchRecentlyPlayedForUser("user-1"),
        AppOutboxEvent.UpdateUserProfileForUser("user-1"),
    )

    @Test
    fun `every AppOutboxEvent returns a non-blank deduplication key`() {
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
            AppOutboxEvent.FetchRecentlyPlayedForUser(userId),
            AppOutboxEvent.UpdateUserProfileForUser(userId),
        ).forEach { event ->
            assertThat(event.deduplicationKey())
                .describedAs("deduplicationKey for ${event::class.simpleName} should contain userId")
                .contains(userId)
        }
    }

    @Test
    fun `payload round-trip restores original event`() {
        allEvents.forEach { event ->
            val restored = AppOutboxEvent.fromKey(event.key, event.toPayload())
            assertThat(restored)
                .describedAs("round-trip for ${event::class.simpleName}")
                .isEqualTo(event)
        }
    }

    @Test
    fun `OutboxHandlerPort has a handler method for every AppOutboxEvent type`() {
        val handlerMethodNames = OutboxHandlerPort::class.java.methods.map { it.name }.toSet()
        allEvents.forEach { event ->
            val simpleName = event::class.simpleName ?: error("Anonymous sealed subclass not allowed")
            val expectedMethod = "handle$simpleName"
            assertThat(handlerMethodNames)
                .describedAs("OutboxHandlerPort should have method $expectedMethod for event $simpleName")
                .contains(expectedMethod)
        }
    }
}
