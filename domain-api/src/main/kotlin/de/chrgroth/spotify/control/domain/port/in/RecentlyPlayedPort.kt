package de.chrgroth.spotify.control.domain.port.`in`

interface RecentlyPlayedPort {
    fun fetchAndPersistForAllUsers()
}
