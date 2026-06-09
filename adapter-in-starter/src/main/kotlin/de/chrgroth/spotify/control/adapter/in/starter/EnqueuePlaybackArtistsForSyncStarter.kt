package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.quarkus.starters.domain.Starter
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class EnqueuePlaybackArtistsForSyncStarter(
  private val catalog: CatalogPort,
) : Starter {

  override val id = "EnqueuePlaybackArtistsForSyncStarter-v1"

  override fun execute() {
    catalog.enqueuePlaybackArtistsForSync()
  }
}
