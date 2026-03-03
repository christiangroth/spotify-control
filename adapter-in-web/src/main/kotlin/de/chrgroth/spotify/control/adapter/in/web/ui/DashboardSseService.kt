package de.chrgroth.spotify.control.adapter.`in`.web.ui

import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.MultiEmitter
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.CopyOnWriteArrayList

@ApplicationScoped
class DashboardSseService {

    private val emitters = CopyOnWriteArrayList<MultiEmitter<in String>>()

    fun stream(): Multi<String> = Multi.createFrom().emitter { emitter ->
        emitters.add(emitter)
        emitter.onTermination { emitters.remove(emitter) }
    }

    fun emit(event: String) {
        emitters.forEach { runCatching { it.emit(event) } }
    }
}
