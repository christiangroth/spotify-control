package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.port.out.infra.DashboardRefreshPort
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.MultiEmitter
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.CopyOnWriteArrayList

@ApplicationScoped
class DashboardSseAdapter : DashboardRefreshPort {

  private val emitters = CopyOnWriteArrayList<MultiEmitter<in String>>()

  fun stream(): Multi<String> = Multi.createFrom().emitter { emitter ->
    emitters.add(emitter)
    emitter.onTermination { emitters.remove(emitter) }
  }

  override fun notifyUserPlaybackData() = emitAll("refresh-playback-data")

  override fun notifyUserPlaylistMetadata() = emitAll("refresh-playlist-metadata")

  override fun notifyUserPlaylistChecks() = emitAll("refresh-playlist-checks")

  override fun notifyCatalogData() = emitAll("refresh-catalog-data")

  private fun emitAll(event: String) = emitters.forEach { runCatching { it.emit(event) } }
}
