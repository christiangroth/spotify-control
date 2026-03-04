package de.chrgroth.spotify.control.domain.port.out

import de.chrgroth.spotify.control.domain.model.MongoCollectionStats
import de.chrgroth.spotify.control.domain.model.MongoQueryStats

interface MongoStatsPort {
    fun getCollectionStats(): List<MongoCollectionStats>
    fun getQueryStats(): List<MongoQueryStats>
}
