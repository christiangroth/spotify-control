package de.chrgroth.spotify.control.domain.port.out.infra

import de.chrgroth.spotify.control.domain.model.infra.MongoQueryStats

interface MongoQueryStatsPort {
  fun current(): List<MongoQueryStats>
}
