package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.RecentlyPartialPlayedItem
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.RecentlyPartialPlayedRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
class RecentlyPartialPlayedRepositoryAdapter : RecentlyPartialPlayedRepositoryPort {

    @Inject
    lateinit var recentlyPartialPlayedDocumentRepository: SpotifyRecentlyPartialPlayedDocumentRepository

    @Inject
    lateinit var mongoQueryMetrics: MongoQueryMetrics

    override fun findExistingPlayedAts(userId: UserId, playedAts: Set<Instant>): Set<Instant> {
        if (playedAts.isEmpty()) return emptySet()
        val javaPlayedAts = playedAts.map { it.toJavaInstant() }
        return mongoQueryMetrics.timedWithFallback("recently_partial_played.findExistingPlayedAts", emptySet()) {
            recentlyPartialPlayedDocumentRepository
                .list("spotifyUserId = ?1 and playedAt in ?2", userId.value, javaPlayedAts)
                .map { it.playedAt.toKotlinInstant() }
                .toSet()
        }
    }

    override fun findSince(userId: UserId, since: Instant?): List<RecentlyPartialPlayedItem> =
        mongoQueryMetrics.timedWithFallback("recently_partial_played.findSince", emptyList()) {
            val query = if (since != null) {
                recentlyPartialPlayedDocumentRepository.list(
                    "spotifyUserId = ?1 and playedAt > ?2",
                    userId.value,
                    since.toJavaInstant(),
                )
            } else {
                recentlyPartialPlayedDocumentRepository.list("spotifyUserId = ?1", userId.value)
            }
            query.map { doc ->
                RecentlyPartialPlayedItem(
                    spotifyUserId = UserId(doc.spotifyUserId),
                    trackId = doc.trackId,
                    trackName = doc.trackName,
                    artistIds = doc.artistIds,
                    artistNames = doc.artistNames,
                    playedAt = doc.playedAt.toKotlinInstant(),
                    playedSeconds = doc.playedSeconds,
                )
            }
        }

    override fun saveAll(items: List<RecentlyPartialPlayedItem>) {
        if (items.isEmpty()) return
        val documents = items.map { item ->
            SpotifyRecentlyPartialPlayedDocument().apply {
                spotifyUserId = item.spotifyUserId.value
                trackId = item.trackId
                trackName = item.trackName
                artistIds = item.artistIds
                artistNames = item.artistNames
                playedAt = item.playedAt.toJavaInstant()
                playedSeconds = item.playedSeconds
            }
        }
        logger.info { "Saving ${documents.size} recently partial played documents" }
        mongoQueryMetrics.timedWithFallback("recently_partial_played.saveAll", Unit) {
            recentlyPartialPlayedDocumentRepository.persist(documents)
        }
    }

    companion object : KLogging()
}
