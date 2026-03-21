package de.chrgroth.spotify.control.domain.port.out

interface MongoReadTimeoutPort {
    fun <T> timedWithFallback(operation: String, fallback: T, block: () -> T): T
}
