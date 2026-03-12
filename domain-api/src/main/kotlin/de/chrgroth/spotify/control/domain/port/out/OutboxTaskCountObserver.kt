package de.chrgroth.spotify.control.domain.port.out

interface OutboxTaskCountObserver {
    fun onOutboxTaskCountChanged()
}
