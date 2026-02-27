package de.chrgroth.spotify.control.domain.port.`in`

interface FetchAllRecentlyPlayedPort {
    fun fetchAndPersistForAllUsers()
}
