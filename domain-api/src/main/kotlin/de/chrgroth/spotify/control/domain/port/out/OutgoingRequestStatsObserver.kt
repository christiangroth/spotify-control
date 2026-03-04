package de.chrgroth.spotify.control.domain.port.out

interface OutgoingRequestStatsObserver {
    fun onRequestRecorded()
}
