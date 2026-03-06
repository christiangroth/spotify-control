package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItemComputed
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.ComputedRecentlyPlayedRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
class ComputedRecentlyPlayedRepositoryAdapter : ComputedRecentlyPlayedRepositoryPort {

    @Inject
    lateinit var computedRecentlyPlayedDocumentRepository: ComputedRecentlyPlayedDocumentRepository

    @Inject
    lateinit var mongoQueryMetrics: MongoQueryMetrics

    override fun findExistingPlayedAts(userId: UserId, playedAts: Set<Instant>): Set<Instant> {
        if (playedAts.isEmpty()) return emptySet()
        val javaPlayedAts = playedAts.map { it.toJavaInstant() }
        return mongoQueryMetrics.timed("computed_recently_played.findExistingPlayedAts") {
            computedRecentlyPlayedDocumentRepository
                .list("spotifyUserId = ?1 and playedAt in ?2", userId.value, javaPlayedAts)
                .map { it.playedAt.toKotlinInstant() }
                .toSet()
        }
    }

    override fun saveAll(items: List<RecentlyPlayedItemComputed>) {
        if (items.isEmpty()) return
        val documents = items.map { item ->
            ComputedRecentlyPlayedDocument().apply {
                spotifyUserId = item.spotifyUserId.value
                trackId = item.trackId
                trackName = item.trackName
                artistIds = item.artistIds
                artistNames = item.artistNames
                playedAt = item.playedAt.toJavaInstant()
                playedMs = item.playedMs
            }
        }
        logger.info { "Saving ${documents.size} computed recently played documents" }
        mongoQueryMetrics.timed("computed_recently_played.saveAll") {
            computedRecentlyPlayedDocumentRepository.persist(documents)
        }
    }

    companion object : KLogging()
}
