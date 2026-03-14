package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.port.out.SyncPoolStatePort
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.atomic.AtomicBoolean

@ApplicationScoped
class SyncPoolState : SyncPoolStatePort {

    private val usingSyncPool = AtomicBoolean(true)

    override fun isUsingSyncPool(): Boolean = usingSyncPool.get()

    override fun disableSyncPool() {
        usingSyncPool.set(false)
    }
}
