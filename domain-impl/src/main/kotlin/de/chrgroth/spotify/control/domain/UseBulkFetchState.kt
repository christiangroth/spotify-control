package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.port.out.UseBulkFetchStatePort
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.atomic.AtomicBoolean

@ApplicationScoped
class UseBulkFetchState : UseBulkFetchStatePort {

    // Starts true after each startup; set to false when a bulk Spotify endpoint signals it is gone.
    // Resets to true automatically on service restart.
    private val usingBulkFetch = AtomicBoolean(true)

    override fun isUsingBulkFetch(): Boolean = usingBulkFetch.get()

    override fun disableBulkFetch() {
        usingBulkFetch.set(false)
    }
}
