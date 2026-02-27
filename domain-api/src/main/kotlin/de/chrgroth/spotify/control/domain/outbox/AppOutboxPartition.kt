package de.chrgroth.spotify.control.domain.outbox

sealed interface AppOutboxPartition {
    val key: String

    data object Spotify : AppOutboxPartition {
        override val key = "spotify"
    }

    companion object {
        val entries: List<AppOutboxPartition> = listOf(Spotify)
    }
}
