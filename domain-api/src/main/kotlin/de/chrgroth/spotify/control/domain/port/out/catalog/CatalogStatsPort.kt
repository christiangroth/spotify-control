package de.chrgroth.spotify.control.domain.port.out.catalog

import de.chrgroth.spotify.control.domain.model.catalog.CatalogStats

interface CatalogStatsPort {
  fun current(): CatalogStats
}
