package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogPort
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import java.time.LocalDate
import java.time.ZoneOffset

@ApplicationScoped
@Suppress("Unused")
class ArtistCatalogSyncJob(
  private val catalog: CatalogPort,
) {

  @Scheduled(cron = "0 0 2 * * ?", skipExecutionIf = PausedSkipPredicate::class)
  fun run() {
    val partition = LocalDate.now(ZoneOffset.UTC).dayOfYear % TOTAL_PARTITIONS
    catalog.enqueueArtistAlbumsSync(partition, TOTAL_PARTITIONS)
  }

  companion object {
    private const val TOTAL_PARTITIONS = 14
  }
}
