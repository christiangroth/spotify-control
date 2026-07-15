package de.chrgroth.spotify.control.domain.port.out.infra

import de.chrgroth.spotify.control.domain.model.infra.MongoCollectionStats

interface MongoCollectionStatsPort {
  fun current(): List<MongoCollectionStats>
}
