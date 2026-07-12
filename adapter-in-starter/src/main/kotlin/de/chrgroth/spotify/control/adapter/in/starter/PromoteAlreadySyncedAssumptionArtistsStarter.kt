package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.quarkus.starters.domain.Starter
import de.chrgroth.spotify.control.domain.model.catalog.ArtistSyncStatus
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

// Follow-up fix for MigrateArtistBlockedFlagToSyncStatusStarter (see #748 Shallow Artists), which incorrectly put
// every pre-existing artist into an assumption status, forcing manual confirmation for artists that were already
// fully synced. Any assumption-status artist that already has at least one album in the catalog was clearly
// already synced before this feature existed, so it is promoted straight to its definitive status. No outbox
// events or aggregation rebuilds are enqueued: aggregation/sync-eligibility treat the assumption and definitive
// statuses identically, only the "needs confirmation" UI state changes.
@ApplicationScoped
@Suppress("Unused")
class PromoteAlreadySyncedAssumptionArtistsStarter(
  private val appArtistRepository: AppArtistRepositoryPort,
  private val appAlbumRepository: AppAlbumRepositoryPort,
) : Starter {

  override val id = "PromoteAlreadySyncedAssumptionArtistsStarter-v1"

  override fun execute() {
    val assumptionArtists = appArtistRepository.findAll().filter { it.syncStatus in ASSUMPTION_STATUSES }
    if (assumptionArtists.isEmpty()) {
      return
    }

    val albumCountByArtistId = appAlbumRepository.countByArtistIds(assumptionArtists.map { it.id }.toSet())
    var promoted = 0
    assumptionArtists.forEach { artist ->
      if ((albumCountByArtistId[artist.id.value] ?: 0L) > 0) {
        val definitiveStatus = if (artist.syncStatus == ArtistSyncStatus.SHALLOW_ASSUMPTION) ArtistSyncStatus.SHALLOW else ArtistSyncStatus.SYNC
        appArtistRepository.setSyncStatus(artist.id, definitiveStatus)
        promoted++
      }
    }
    logger.info { "Promoted $promoted already-synced artist(s) out of assumption status to their definitive sync status" }
  }

  companion object : KLogging() {
    private val ASSUMPTION_STATUSES = setOf(ArtistSyncStatus.SYNC_ASSUMPTION, ArtistSyncStatus.SHALLOW_ASSUMPTION)
  }
}
