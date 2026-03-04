package de.chrgroth.spotify.control.util.starters

import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.atomic.AtomicBoolean

@ApplicationScoped
class StarterCompletionFlag {

    private val completed = AtomicBoolean(false)

    fun markCompleted() {
        completed.set(true)
    }

    fun isCompleted(): Boolean = completed.get()
}
