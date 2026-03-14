package de.chrgroth.spotify.control.domain.port.out

interface UseBulkFetchStatePort {
    fun isUsingBulkFetch(): Boolean
    fun disableBulkFetch()
}
