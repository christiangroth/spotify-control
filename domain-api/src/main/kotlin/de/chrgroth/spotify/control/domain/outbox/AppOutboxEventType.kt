package de.chrgroth.spotify.control.domain.outbox

sealed interface AppOutboxEventType {
    val key: String

    data object PollRecentlyPlayed : AppOutboxEventType {
        override val key = "PollRecentlyPlayed"
    }

    companion object {
        private val byKey: Map<String, AppOutboxEventType> = listOf(PollRecentlyPlayed).associateBy { it.key }

        fun fromKey(key: String): AppOutboxEventType =
            byKey[key] ?: throw IllegalArgumentException("Unknown outbox event type key: $key")
    }
}
